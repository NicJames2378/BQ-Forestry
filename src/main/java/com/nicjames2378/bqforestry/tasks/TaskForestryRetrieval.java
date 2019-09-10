package com.nicjames2378.bqforestry.tasks;

import betterquesting.api.api.ApiReference;
import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.IItemTask;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api2.cache.CapabilityProviderQuestCache;
import betterquesting.api2.cache.QuestCache;
import betterquesting.api2.client.gui.misc.IGuiRect;
import betterquesting.api2.client.gui.panels.IGuiPanel;
import betterquesting.api2.storage.DBEntry;
import com.nicjames2378.bqforestry.Main;
import com.nicjames2378.bqforestry.client.gui.editors.tasks.GuiEditTaskBeeRetrievalLanding;
import com.nicjames2378.bqforestry.client.tasks.PanelTaskForestryRetrieval;
import com.nicjames2378.bqforestry.config.ConfigHandler;
import com.nicjames2378.bqforestry.tasks.factory.FactoryTaskForestryRetrieval;
import com.nicjames2378.bqforestry.utils.Reference;
import com.nicjames2378.bqforestry.utils.UtilitiesBee;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.Level;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

import static com.nicjames2378.bqforestry.utils.UtilitiesBee.*;

public class TaskForestryRetrieval implements ITaskInventory, IItemTask {
    private static BigItemStack defBee;

    public static BigItemStack getDefaultBee() {
        return new BigItemStack(getBaseBee(UtilitiesBee.DEFAULT_SPECIES, UtilitiesBee.BeeTypes.valueOf(ConfigHandler.cfgBeeType), ConfigHandler.cfgOnlyMated));
    }

    public final NonNullList<BigItemStack> requiredItems = new NonNullList<BigItemStack>() {
        {
            add(getDefaultBee());
        }
    };
    private final Set<UUID> completeUsers = new TreeSet<>();
    private final HashMap<UUID, int[]> userProgress = new HashMap<>();
    public boolean consume = ConfigHandler.cfgConsume;
    public boolean autoConsume = ConfigHandler.cfgAutoConsume;

    @Override
    public String getUnlocalisedName() {
        return Reference.MOD_ID + ".task.bee_retrieval";
    }

    @Override
    public ResourceLocation getFactoryID() {
        return FactoryTaskForestryRetrieval.INSTANCE.getRegistryName();
    }

    @Override
    public boolean isComplete(UUID uuid) {
        return completeUsers.contains(uuid);
    }

    @Override
    public void setComplete(UUID uuid) {
        completeUsers.add(uuid);
    }

    @Override
    public void onInventoryChange(@Nonnull DBEntry<IQuest> quest, @Nonnull EntityPlayer player) {
        if (!consume || autoConsume) {
            detect(player, quest.getValue());
        }
    }

    @Override
    public void detect(EntityPlayer player, IQuest quest) {
        UUID playerID = QuestingAPI.getQuestingUUID(player);

        if (player.inventory == null || isComplete(playerID)) return;

        int[] progress = this.getUsersProgress(playerID);
        boolean updated = false;

        /*if(!consume)
        {
            if(groupDetect) // Reset all detect progress
            {
                Arrays.fill(progress, 0);
            } else
            {
                for(int i = 0; i < progress.length; i++)
                {
                    if(progress[i] != 0 && progress[i] < requiredItems.get(i).stackSize) // Only reset progress for incomplete entries
                    {
                        progress[i] = 0;
                        updated = true;
                    }
                }
            }
        }*/

        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            int remStack = stack.getCount(); // Allows the stack detection to split across multiple requirements

            for (int j = 0; j < requiredItems.size(); j++) {
                BigItemStack rStack = requiredItems.get(j);

                if (progress[j] >= rStack.stackSize) continue;

                if (isMated(rStack.getBaseStack()) && !isMated(stack))
                    continue;

                if (checkMatchSpecies(rStack.getBaseStack(), stack)) {
                    int remaining = rStack.stackSize - progress[j];
                    if (consume) {
                        ItemStack removed = player.inventory.decrStackSize(i, remaining);
                        progress[j] += removed.getCount();
                    } else {
                        int temp = Math.min(remaining, remStack);
                        remStack -= temp;
                        progress[j] += temp;
                    }

                    updated = true;
                }
            }
        }

        if (updated) setUserProgress(playerID, progress);

        boolean hasAll = true; //flag
        int[] totalProgress = getUsersProgress(playerID);

        for (int j = 0; j < requiredItems.size(); j++) {
            BigItemStack rStack = requiredItems.get(j);

            if (totalProgress[j] >= rStack.stackSize) continue;

            hasAll = false;
            break;
        }

        if (hasAll) {
            setComplete(playerID);
            updated = true;
        }

        if (updated) {
            QuestCache qc = player.getCapability(CapabilityProviderQuestCache.CAP_QUEST_CACHE, null);
            if (qc != null) qc.markQuestDirty(QuestingAPI.getAPI(ApiReference.QUEST_DB).getID(quest));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound json) {
        json.setBoolean("consume", consume);
        json.setBoolean("autoConsume", autoConsume);

        NBTTagList itemArray = new NBTTagList();
        for (BigItemStack stack : this.requiredItems) {
            itemArray.appendTag(JsonHelper.ItemStackToJson(stack, new NBTTagCompound()));
        }
        json.setTag("requiredItems", itemArray);

        return json;
    }

    @Override
    public void readFromNBT(NBTTagCompound json) {
        consume = json.getBoolean("consume");
        autoConsume = json.getBoolean("autoConsume");

        requiredItems.clear();
        NBTTagList iList = json.getTagList("requiredItems", 10);
        for (int i = 0; i < iList.tagCount(); i++) {
            requiredItems.add(JsonHelper.JsonToItemStack(iList.getCompoundTagAt(i)));
        }
    }

    @Override
    public void readProgressFromNBT(NBTTagCompound nbt, boolean merge) {
        if (!merge) {
            completeUsers.clear();
            userProgress.clear();
        }

        NBTTagList cList = nbt.getTagList("completeUsers", 8);
        for (int i = 0; i < cList.tagCount(); i++) {
            try {
                completeUsers.add(UUID.fromString(cList.getStringTagAt(i)));
            } catch (Exception e) {
                Main.log.log(Level.ERROR, "Unable to load UUID for task", e);
            }
        }

        NBTTagList pList = nbt.getTagList("userProgress", 10);
        for (int n = 0; n < pList.tagCount(); n++) {
            try {
                NBTTagCompound pTag = pList.getCompoundTagAt(n);
                UUID uuid = UUID.fromString(pTag.getString("uuid"));

                int[] data = new int[requiredItems.size()];
                NBTTagList dNbt = pTag.getTagList("data", 3);
                for (int i = 0; i < data.length && i < dNbt.tagCount(); i++) {
                    data[i] = dNbt.getIntAt(i);
                }

                userProgress.put(uuid, data);
            } catch (Exception e) {
                Main.log.log(Level.ERROR, "Unable to load user progress for task", e);
            }
        }
    }

    @Override
    public NBTTagCompound writeProgressToNBT(NBTTagCompound nbt, @Nullable List<UUID> users) {
        NBTTagList jArray = new NBTTagList();
        NBTTagList progArray = new NBTTagList();

        if (users != null) {
            users.forEach((uuid) -> {
                if (completeUsers.contains(uuid)) jArray.appendTag(new NBTTagString(uuid.toString()));

                int[] data = userProgress.get(uuid);
                if (data != null) {
                    NBTTagCompound pJson = new NBTTagCompound();
                    pJson.setString("uuid", uuid.toString());
                    NBTTagList pArray = new NBTTagList();
                    for (int i : data) pArray.appendTag(new NBTTagInt(i));
                    pJson.setTag("data", pArray);
                    progArray.appendTag(pJson);
                }
            });
        } else {
            completeUsers.forEach((uuid) -> jArray.appendTag(new NBTTagString(uuid.toString())));

            userProgress.forEach((uuid, data) -> {
                NBTTagCompound pJson = new NBTTagCompound();
                pJson.setString("uuid", uuid.toString());
                NBTTagList pArray = new NBTTagList();
                for (int i : data) pArray.appendTag(new NBTTagInt(i));
                pJson.setTag("data", pArray);
                progArray.appendTag(pJson);
            });
        }

        nbt.setTag("completeUsers", jArray);
        nbt.setTag("userProgress", progArray);

        return nbt;
    }

    @Override
    public void resetUser(@Nullable UUID uuid) {
        if (uuid == null) {
            completeUsers.clear();
            userProgress.clear();
        } else {
            completeUsers.remove(uuid);
            userProgress.remove(uuid);
        }
    }

    @Override
    public void resetAll() {
        resetUser(null);
    }

    @Override
    public IGuiPanel getTaskGui(IGuiRect rect, IQuest quest) {
        return new PanelTaskForestryRetrieval(rect, this);
    }

    @Override
    public boolean canAcceptItem(UUID owner, IQuest quest, ItemStack stack) {
        if (owner == null || stack == null || stack.isEmpty() || !consume || isComplete(owner) || requiredItems.size() <= 0)
            return false;

        int[] progress = getUsersProgress(owner);

        for (int j = 0; j < requiredItems.size(); j++) {
            BigItemStack rStack = requiredItems.get(j);

            if (progress[j] >= rStack.stackSize) continue;
            if (isMated(rStack.getBaseStack()) && !isMated(stack)) continue;

            if (checkMatchSpecies(rStack.getBaseStack(), stack)) return true;
        }

        return false;
    }



    @Override
    public ItemStack submitItem(UUID owner, IQuest quest, ItemStack input) {
        if (owner == null || input.isEmpty() || !consume || isComplete(owner)) return input;

        ItemStack stack = input.copy();

        int[] progress = getUsersProgress(owner);
        boolean updated = false;

        for (int j = 0; j < requiredItems.size(); j++) {
            if (stack.isEmpty()) break;

            BigItemStack rStack = requiredItems.get(j);

            if (progress[j] >= rStack.stackSize) continue;

            int remaining = rStack.stackSize - progress[j];

            if (checkMatchSpecies(rStack.getBaseStack(), stack)) {
                int removed = Math.min(stack.getCount(), remaining);
                stack.shrink(removed);
                progress[j] += removed;
                updated = true;
                if (stack.isEmpty()) break;
            }
        }

        if (updated) {
            setUserProgress(owner, progress);
        }

        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public GuiScreen getTaskEditor(GuiScreen parent, IQuest quest) {
        return new GuiEditTaskBeeRetrievalLanding(parent, quest, this);
    }

    public void setUserProgress(UUID uuid, int[] progress) {
        userProgress.put(uuid, progress);
    }

    public int[] getUsersProgress(UUID uuid) {
        int[] progress = userProgress.get(uuid);
        return progress == null || progress.length != requiredItems.size() ? new int[requiredItems.size()] : progress;
    }

    private void bulkMarkDirty(@Nonnull List<UUID> uuids, int questID) {
        if (uuids.size() <= 0) return;
        final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        uuids.forEach((value) -> {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(value);
            //noinspection ConstantConditions
            if (player == null) return;
            QuestCache qc = player.getCapability(CapabilityProviderQuestCache.CAP_QUEST_CACHE, null);
            if (qc != null) qc.markQuestDirty(questID);
        });
    }

    private List<Tuple<UUID, int[]>> getBulkProgress(@Nonnull List<UUID> uuids) {
        if (uuids.size() <= 0) return Collections.emptyList();
        List<Tuple<UUID, int[]>> list = new ArrayList<>();
        uuids.forEach((key) -> list.add(new Tuple<>(key, getUsersProgress(key))));
        return list;
    }

    private void setBulkProgress(@Nonnull List<Tuple<UUID, int[]>> list) {
        list.forEach((entry) -> setUserProgress(entry.getFirst(), entry.getSecond()));
    }
}
