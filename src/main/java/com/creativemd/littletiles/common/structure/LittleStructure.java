package com.creativemd.littletiles.common.structure;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.annotation.Nullable;

import com.creativemd.creativecore.common.packet.PacketHandler;
import com.creativemd.creativecore.common.utils.math.Rotation;
import com.creativemd.creativecore.common.utils.math.RotationUtils;
import com.creativemd.creativecore.common.utils.mc.WorldUtils;
import com.creativemd.creativecore.common.utils.type.HashMapList;
import com.creativemd.creativecore.common.utils.type.Pair;
import com.creativemd.creativecore.common.world.SubWorld;
import com.creativemd.littletiles.LittleTiles;
import com.creativemd.littletiles.client.render.tile.LittleRenderBox;
import com.creativemd.littletiles.common.action.LittleActionException;
import com.creativemd.littletiles.common.action.block.LittleActionActivated;
import com.creativemd.littletiles.common.entity.EntityAnimation;
import com.creativemd.littletiles.common.packet.LittleUpdateStructurePacket;
import com.creativemd.littletiles.common.structure.connection.StructureChildConnection;
import com.creativemd.littletiles.common.structure.connection.StructureChildFromSubWorldConnection;
import com.creativemd.littletiles.common.structure.connection.StructureChildToSubWorldConnection;
import com.creativemd.littletiles.common.structure.directional.StructureDirectionalField;
import com.creativemd.littletiles.common.structure.exception.CorruptedConnectionException;
import com.creativemd.littletiles.common.structure.exception.MissingBlockException;
import com.creativemd.littletiles.common.structure.exception.MissingChildException;
import com.creativemd.littletiles.common.structure.exception.MissingParentException;
import com.creativemd.littletiles.common.structure.exception.MissingStructureException;
import com.creativemd.littletiles.common.structure.exception.NotYetConnectedException;
import com.creativemd.littletiles.common.structure.registry.LittleStructureType;
import com.creativemd.littletiles.common.tile.LittleTile;
import com.creativemd.littletiles.common.tile.LittleTile.LittleTilePosition;
import com.creativemd.littletiles.common.tile.math.location.StructureLocation;
import com.creativemd.littletiles.common.tile.math.vec.LittleAbsoluteVec;
import com.creativemd.littletiles.common.tile.math.vec.LittleVec;
import com.creativemd.littletiles.common.tile.math.vec.LittleVecContext;
import com.creativemd.littletiles.common.tile.math.vec.RelativeBlockPos;
import com.creativemd.littletiles.common.tile.parent.IStructureTileList;
import com.creativemd.littletiles.common.tile.parent.StructureTileList;
import com.creativemd.littletiles.common.tile.preview.LittleAbsolutePreviews;
import com.creativemd.littletiles.common.tile.preview.LittlePreview;
import com.creativemd.littletiles.common.tile.preview.LittlePreviews;
import com.creativemd.littletiles.common.tile.preview.LittlePreviewsStructureHolder;
import com.creativemd.littletiles.common.tileentity.TileEntityLittleTiles;
import com.creativemd.littletiles.common.util.grid.LittleGridContext;
import com.creativemd.littletiles.common.util.outdated.identifier.LittleIdentifierRelative;
import com.creativemd.littletiles.common.util.vec.LittleTransformation;
import com.creativemd.littletiles.common.util.vec.SurroundingBox;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumFacing.Axis;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public abstract class LittleStructure {
	
	private static final Iterator<LittleTile> EMPTY_ITERATOR = new Iterator<LittleTile>() {
		
		@Override
		public boolean hasNext() {
			return false;
		}
		
		@Override
		public LittleTile next() {
			return null;
		}
		
	};
	
	private static final HashMapList<BlockPos, LittleTile> EMPTY_HASHMAPLIST = new HashMapList<>();
	
	public final LittleStructureType type;
	public final IStructureTileList mainBlock;
	private final List<StructureBlockConnector> blocks = new ArrayList<>();
	
	public String name;
	
	private StructureChildConnection parent;
	private List<StructureChildConnection> children;
	
	public LittleStructure(LittleStructureType type, IStructureTileList mainBlock) {
		this.type = type;
		this.mainBlock = mainBlock;
	}
	
	// ================Basics================
	
	public World getWorld() {
		return mainBlock.getWorld();
	}
	
	public BlockPos getPos() {
		return mainBlock.getPos();
	}
	
	public int getIndex() {
		return mainBlock.getIndex();
	}
	
	public int getAttribute() {
		return type.attribute;
	}
	
	public StructureLocation getStructureLoaction() {
		return new StructureLocation(this);
	}
	
	// ================Connections================
	
	public void load() throws CorruptedConnectionException, NotYetConnectedException {
		for (StructureBlockConnector block : blocks)
			block.connect();
		
		try {
			if (parent != null)
				parent.checkConnection();
		} catch (CorruptedConnectionException e) {
			throw new MissingParentException(parent, e);
		}
		
		for (StructureChildConnection child : children)
			try {
				child.getStructure().load();
			} catch (CorruptedConnectionException e) {
				throw new MissingChildException(child, e);
			}
	}
	
	public int count() throws CorruptedConnectionException, NotYetConnectedException {
		int count = mainBlock.size();
		for (StructureBlockConnector block : blocks)
			count += block.count();
		return count;
	}
	
	public boolean isChildOf(LittleStructure structure) throws CorruptedConnectionException, NotYetConnectedException {
		if (structure == this)
			return true;
		if (parent != null)
			return parent.getStructure().isChildOf(structure);
		return false;
	}
	
	public void updateChildConnection(int i, LittleStructure child) {
		World world = getWorld();
		World childWorld = child.getWorld();
		
		StructureChildConnection connector;
		if (childWorld == world)
			connector = new StructureChildConnection(this, false, i, child.getPos().subtract(getPos()), child.getIndex(), child.getAttribute());
		else if (childWorld instanceof SubWorld && ((SubWorld) childWorld).parent != null)
			connector = new StructureChildToSubWorldConnection(this, i, child.getPos().subtract(getPos()), child.getIndex(), child.getAttribute(), ((SubWorld) childWorld).parent.getUniqueID());
		else
			throw new RuntimeException("Invalid connection between to structures!");
		
		if (children.size() > i)
			children.set(i, connector);
		else if (children.size() == i)
			children.add(connector);
		else
			throw new RuntimeException("Invalid childId " + children.size() + ".");
	}
	
	public void updateParentConnection(int i, LittleStructure parent) {
		World world = getWorld();
		World parentWorld = parent.getWorld();
		
		StructureChildConnection connector;
		if (parentWorld == world)
			connector = new StructureChildConnection(this, true, i, parent.getPos().subtract(getPos()), parent.getIndex(), parent.getAttribute());
		else if (world instanceof SubWorld && ((SubWorld) world).parent != null)
			connector = new StructureChildFromSubWorldConnection(this, i, parent.getPos().subtract(getPos()), parent.getIndex(), parent.getAttribute());
		else
			throw new RuntimeException("Invalid connection between to structures!");
		
		this.parent = connector;
	}
	
	public StructureChildConnection getParent() {
		return parent;
	}
	
	public List<StructureChildConnection> getChildren() {
		return children;
	}
	
	public StructureChildConnection getChild(int index) {
		return children.get(index);
	}
	
	// ================Tiles================
	
	public void addBlock(StructureTileList block) {
		blocks.add(new StructureBlockConnector(block.getPos().subtract(getPos())));
	}
	
	public Iterable<TileEntityLittleTiles> blocks() throws CorruptedConnectionException, NotYetConnectedException {
		load();
		return new Iterable<TileEntityLittleTiles>() {
			
			@Override
			public Iterator<TileEntityLittleTiles> iterator() {
				
				return new Iterator<TileEntityLittleTiles>() {
					
					boolean first = true;
					Iterator<StructureBlockConnector> iterator = blocks.iterator();
					
					@Override
					public boolean hasNext() {
						return first || iterator.hasNext();
					}
					
					@Override
					public TileEntityLittleTiles next() {
						if (first) {
							first = false;
							return mainBlock.getTe();
						}
						try {
							return iterator.next().getTileEntity();
						} catch (CorruptedConnectionException | NotYetConnectedException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
				};
			}
		};
	}
	
	public Iterable<IStructureTileList> blocksList() throws CorruptedConnectionException, NotYetConnectedException {
		load();
		return new Iterable<IStructureTileList>() {
			
			@Override
			public Iterator<IStructureTileList> iterator() {
				
				return new Iterator<IStructureTileList>() {
					
					boolean first = true;
					Iterator<StructureBlockConnector> iterator = blocks.iterator();
					
					@Override
					public boolean hasNext() {
						return first || iterator.hasNext();
					}
					
					@Override
					public IStructureTileList next() {
						if (first) {
							first = false;
							return mainBlock;
						}
						try {
							return iterator.next().getList();
						} catch (CorruptedConnectionException | NotYetConnectedException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
				};
			}
		};
	}
	
	public Iterable<Pair<IStructureTileList, LittleTile>> tiles() throws CorruptedConnectionException, NotYetConnectedException {
		Iterator<IStructureTileList> iterator = blocksList().iterator();
		return new Iterable<Pair<IStructureTileList, LittleTile>>() {
			
			@Override
			public Iterator<Pair<IStructureTileList, LittleTile>> iterator() {
				return new Iterator<Pair<IStructureTileList, LittleTile>>() {
					
					Iterator<LittleTile> inBlock = null;
					Pair<IStructureTileList, LittleTile> pair = null;
					
					@Override
					public boolean hasNext() {
						while (inBlock == null || !inBlock.hasNext()) {
							if (!iterator.hasNext())
								return false;
							IStructureTileList list = iterator.next();
							pair = new Pair<>(list, null);
							inBlock = list.iterator();
						}
						return true;
					}
					
					@Override
					public Pair<IStructureTileList, LittleTile> next() {
						pair.setValue(inBlock.next());
						return pair;
					}
				};
			}
		};
	}
	
	public HashMapList<BlockPos, IStructureTileList> collectAllBlocksList() throws CorruptedConnectionException, NotYetConnectedException {
		return collectAllBlocksList(new HashMapList<>());
	}
	
	protected HashMapList<BlockPos, IStructureTileList> collectAllBlocksList(HashMapList<BlockPos, IStructureTileList> map) throws CorruptedConnectionException, NotYetConnectedException {
		for (IStructureTileList list : blocksList())
			map.add(list.getPos(), list);
		for (StructureChildConnection child : children)
			child.getStructure().collectAllBlocksList(map);
		return map;
	}
	
	// ================Placing================
	
	/** takes name of stack and connects the structure to its children (does so recursively)
	 * 
	 * @param stack
	 */
	public void placedStructure(@Nullable ItemStack stack) {
		NBTTagCompound nbt;
		if (name == null && stack != null && (nbt = stack.getSubCompound("display")) != null && nbt.hasKey("Name", 8))
			name = nbt.getString("Name");
	}
	
	// ================Save and loading================
	
	public void loadFromNBT(NBTTagCompound nbt) {
		blocks.clear();
		
		// LoadTiles
		if (nbt.hasKey("count")) { // very old way (before 1.5)
			int count = nbt.getInteger("count");
			for (int i = 0; i < count; i++) {
				LittleIdentifierRelative coord = null;
				if (nbt.hasKey("i" + i + "coX")) {
					LittleTilePosition pos = new LittleTilePosition("i" + i, nbt);
					coord = new LittleIdentifierRelative(pos.coord.getX() - getPos().getX(), pos.coord.getY() - getPos().getY(), pos.coord.getZ() - getPos().getZ(), LittleGridContext.get(), new int[] { pos.position.x, pos.position.y, pos.position.z });
				} else {
					coord = LittleIdentifierRelative.loadIdentifierOld("i" + i, nbt);
				}
				
				blocks.add(new StructureBlockConnector(coord.coord));
			}
		} else if (nbt.hasKey("tiles")) { // old way (during 1.5)
			NBTTagList list = nbt.getTagList("tiles", 11);
			for (int i = 0; i < list.tagCount(); i++) {
				int[] array = list.getIntArrayAt(i);
				if (array.length == 4) {
					RelativeBlockPos pos = new RelativeBlockPos(array);
					if (!pos.getRelativePos().equals(BlockPos.ORIGIN))
						blocks.add(new StructureBlockConnector(pos.getRelativePos()));
				} else
					System.out.println("Found invalid array! " + nbt);
			}
		} else if (nbt.hasKey("blocks")) { // now (1.5 pre 200)
			blocks.clear();
			int[] array = nbt.getIntArray("blocks");
			for (int i = 0; i + 2 < array.length; i += 3)
				blocks.add(new StructureBlockConnector(new BlockPos(array[i], array[i + 1], array[i + 2])));
		}
		
		if (nbt.hasKey("name"))
			name = nbt.getString("name");
		else
			name = null;
		
		// Load family (parent and children)		
		if (nbt.hasKey("parent"))
			parent = StructureChildConnection.loadFromNBT(this, nbt.getCompoundTag("parent"), true);
		else
			parent = null;
		
		if (nbt.hasKey("children")) {
			
			NBTTagList list = nbt.getTagList("children", 10);
			children = new ArrayList<>(list.tagCount());
			for (int i = 0; i < list.tagCount(); i++)
				children.add(StructureChildConnection.loadFromNBT(this, list.getCompoundTagAt(i), false));
			
			if (this instanceof IAnimatedStructure && ((IAnimatedStructure) this).isAnimated())
				for (StructureChildConnection child : children)
					if (child instanceof StructureChildToSubWorldConnection && ((StructureChildToSubWorldConnection) child).entityUUID.equals(((IAnimatedStructure) this).getAnimation().getUniqueID()))
						throw new RuntimeException("Something went wrong during loading!");
					
		} else
			children = new ArrayList<>();
		
		for (StructureDirectionalField field : type.directional) {
			if (nbt.hasKey(field.saveKey))
				field.createAndSet(this, nbt);
			else
				field.set(this, failedLoadingRelative(nbt, field));
		}
		
		loadFromNBTExtra(nbt);
	}
	
	protected Object failedLoadingRelative(NBTTagCompound nbt, StructureDirectionalField field) {
		return field.getDefault();
	}
	
	protected abstract void loadFromNBTExtra(NBTTagCompound nbt);
	
	public NBTTagCompound writeToNBTPreview(NBTTagCompound nbt, BlockPos newCenter) {
		nbt.setString("id", type.id);
		if (name != null)
			nbt.setString("name", name);
		else
			nbt.removeTag("name");
		
		LittleVecContext vec = new LittleVecContext(new LittleVec(mainBlock.getContext(), getPos().subtract(newCenter)), mainBlock.getContext());
		
		LittleVec inverted = vec.getVec().copy();
		inverted.invert();
		
		for (StructureDirectionalField field : type.directional) {
			Object value = field.get(this);
			field.move(value, vec.getContext(), vec.getVec());
			field.save(nbt, value);
			field.move(value, vec.getContext(), inverted);
		}
		
		writeToNBTExtra(nbt);
		return nbt;
	}
	
	public void writeToNBT(NBTTagCompound nbt) {
		nbt.setString("id", type.id);
		if (name != null)
			nbt.setString("name", name);
		else
			nbt.removeTag("name");
		
		// Save family (parent and children)
		if (parent != null)
			nbt.setTag("parent", parent.writeToNBT(new NBTTagCompound()));
		
		if (children != null && !children.isEmpty()) {
			NBTTagList list = new NBTTagList();
			for (StructureChildConnection child : children)
				list.appendTag(child.writeToNBT(new NBTTagCompound()));
			
			nbt.setTag("children", list);
		}
		
		// Save blocks
		int[] array = new int[blocks.size() * 3];
		for (int i = 0; i < blocks.size(); i++) {
			StructureBlockConnector block = blocks.get(i);
			array[i * 3] = block.pos.getX();
			array[i * 3 + 1] = block.pos.getY();
			array[i * 3 + 2] = block.pos.getZ();
		}
		nbt.setIntArray("blocks", array);
		
		for (StructureDirectionalField field : type.directional) {
			Object value = field.get(this);
			field.save(nbt, value);
		}
		
		writeToNBTExtra(nbt);
	}
	
	protected abstract void writeToNBTExtra(NBTTagCompound nbt);
	
	// ====================Destroy====================
	
	public void onLittleTileDestroy() throws CorruptedConnectionException, NotYetConnectedException {
		if (parent != null) {
			parent.getStructure().onLittleTileDestroy();
			return;
		}
		
		load();
		removeStructure();
	}
	
	public void removeStructure() throws CorruptedConnectionException, NotYetConnectedException {
		load();
		onStructureDestroyed();
		
		for (StructureChildConnection child : children)
			child.destroyStructure();
		
		if (this instanceof IAnimatedStructure && ((IAnimatedStructure) this).isAnimated())
			((IAnimatedStructure) this).destroyAnimation();
		else {
			mainBlock.getTe().updateTiles((x) -> x.removeStructure(getIndex()));
			for (StructureBlockConnector block : blocks)
				block.remove();
		}
		
	}
	
	/** Is called before the structure is removed */
	public void onStructureDestroyed() {
		
	}
	
	// ====================Previews====================
	
	public LittleAbsolutePreviews getAbsolutePreviews(BlockPos pos) throws CorruptedConnectionException, NotYetConnectedException {
		NBTTagCompound structureNBT = new NBTTagCompound();
		this.writeToNBTPreview(structureNBT, pos);
		LittleAbsolutePreviews previews = new LittleAbsolutePreviews(structureNBT, pos, LittleGridContext.getMin());
		
		for (Pair<IStructureTileList, LittleTile> pair : tiles())
			previews.addTile(pair.key, pair.value);
		
		for (StructureChildConnection child : children)
			previews.addChild(child.getStructure().getPreviews(pos));
		
		previews.convertToSmallest();
		return previews;
	}
	
	public LittlePreviews getPreviews(BlockPos pos) throws CorruptedConnectionException, NotYetConnectedException {
		NBTTagCompound structureNBT = new NBTTagCompound();
		this.writeToNBTPreview(structureNBT, pos);
		LittlePreviews previews = new LittlePreviews(structureNBT, LittleGridContext.getMin());
		
		for (Pair<IStructureTileList, LittleTile> pair : tiles()) {
			LittlePreview preview = previews.addTile(pair.key, pair.value);
			preview.box.add(new LittleVec(previews.getContext(), pair.key.getPos().subtract(pos)));
		}
		
		for (StructureChildConnection child : children)
			previews.addChild(child.getStructure().getPreviews(pos));
		
		previews.convertToSmallest();
		return previews;
	}
	
	public LittleAbsolutePreviews getAbsolutePreviewsSameWorldOnly(BlockPos pos) throws CorruptedConnectionException, NotYetConnectedException {
		NBTTagCompound structureNBT = new NBTTagCompound();
		this.writeToNBTPreview(structureNBT, pos);
		LittleAbsolutePreviews previews = new LittleAbsolutePreviews(structureNBT, pos, LittleGridContext.getMin());
		
		for (Pair<IStructureTileList, LittleTile> pair : tiles())
			previews.addTile(pair.key, pair.value);
		
		for (StructureChildConnection child : children)
			if (!child.isLinkToAnotherWorld())
				previews.addChild(child.getStructure().getPreviewsSameWorldOnly(pos));
			else
				previews.addChild(new LittlePreviewsStructureHolder(child.getStructure()));
			
		previews.convertToSmallest();
		return previews;
	}
	
	public LittlePreviews getPreviewsSameWorldOnly(BlockPos pos) throws CorruptedConnectionException, NotYetConnectedException {
		NBTTagCompound structureNBT = new NBTTagCompound();
		this.writeToNBTPreview(structureNBT, pos);
		LittlePreviews previews = new LittlePreviews(structureNBT, LittleGridContext.getMin());
		
		for (Pair<IStructureTileList, LittleTile> pair : tiles()) {
			LittlePreview preview = previews.addTile(pair.key, pair.value);
			preview.box.add(new LittleVec(previews.getContext(), pair.key.getPos().subtract(pos)));
		}
		
		for (StructureChildConnection child : children)
			if (!child.isLinkToAnotherWorld())
				previews.addChild(child.getStructure().getPreviews(pos));
			else
				previews.addChild(new LittlePreviewsStructureHolder(child.getStructure()));
			
		previews.convertToSmallest();
		return previews;
	}
	
	public MutableBlockPos getMinPos(MutableBlockPos pos) throws CorruptedConnectionException, NotYetConnectedException {
		for (StructureBlockConnector block : blocks) {
			BlockPos tePos = block.getAbsolutePos();
			pos.setPos(Math.min(pos.getX(), tePos.getX()), Math.min(pos.getY(), tePos.getY()), Math.min(pos.getZ(), tePos.getZ()));
		}
		
		for (StructureChildConnection child : children)
			child.getStructure().getMinPos(pos);
		
		return pos;
	}
	
	// ====================Transform & Transfer====================
	
	public void transferChildrenToAnimation(EntityAnimation animation) throws CorruptedConnectionException, NotYetConnectedException {
		for (StructureChildConnection child : children) {
			LittleStructure childStructure = child.getStructure();
			if (child.isLinkToAnotherWorld()) {
				EntityAnimation subAnimation = ((IAnimatedStructure) childStructure).getAnimation();
				int l1 = subAnimation.chunkCoordX;
				int i2 = subAnimation.chunkCoordZ;
				World world = getWorld();
				if (subAnimation.addedToChunk) {
					Chunk chunk = world.getChunkFromChunkCoords(l1, i2);
					if (chunk != null)
						chunk.removeEntity(subAnimation);
					subAnimation.addedToChunk = false;
				}
				world.loadedEntityList.remove(subAnimation);
				subAnimation.setParentWorld(animation.fakeWorld);
				animation.fakeWorld.spawnEntity(subAnimation);
				subAnimation.updateTickState();
			} else
				childStructure.transferChildrenToAnimation(animation);
		}
	}
	
	public void transferChildrenFromAnimation(EntityAnimation animation) throws CorruptedConnectionException, NotYetConnectedException {
		World parentWorld = animation.fakeWorld.getParent();
		for (StructureChildConnection child : children) {
			LittleStructure childStructure = child.getStructure();
			if (child.isLinkToAnotherWorld()) {
				EntityAnimation subAnimation = ((IAnimatedStructure) childStructure).getAnimation();
				int l1 = subAnimation.chunkCoordX;
				int i2 = subAnimation.chunkCoordZ;
				if (subAnimation.addedToChunk) {
					Chunk chunk = animation.fakeWorld.getChunkFromChunkCoords(l1, i2);
					if (chunk != null)
						chunk.removeEntity(subAnimation);
					subAnimation.addedToChunk = false;
				}
				animation.fakeWorld.loadedEntityList.remove(subAnimation);
				subAnimation.setParentWorld(parentWorld);
				parentWorld.spawnEntity(subAnimation);
				subAnimation.updateTickState();
			} else
				childStructure.transferChildrenFromAnimation(animation);
		}
	}
	
	public void transformAnimation(LittleTransformation transformation) throws CorruptedConnectionException, NotYetConnectedException {
		for (StructureChildConnection child : children) {
			LittleStructure childStructure = child.getStructure();
			if (child.isLinkToAnotherWorld())
				((IAnimatedStructure) childStructure).getAnimation().transformWorld(transformation);
			else
				childStructure.transformAnimation(transformation);
		}
	}
	
	// ====================Helpers====================
	
	public SurroundingBox getSurroundingBox() throws CorruptedConnectionException, NotYetConnectedException {
		SurroundingBox box = new SurroundingBox(true, getWorld());
		box.add(mainBlock);
		for (StructureBlockConnector block : blocks)
			box.add(block.getList());
		return box;
	}
	
	public Vec3d getHighestCenterVec() throws CorruptedConnectionException, NotYetConnectedException {
		return getSurroundingBox().getHighestCenterVec();
	}
	
	public LittleAbsoluteVec getHighestCenterPoint() throws CorruptedConnectionException, NotYetConnectedException {
		return getSurroundingBox().getHighestCenterPoint();
	}
	
	// ====================Packets====================
	
	public void updateStructure() {
		NBTTagCompound nbt = new NBTTagCompound();
		writeToNBT(nbt);
		PacketHandler.sendPacketToTrackingPlayers(new LittleUpdateStructurePacket(getStructureLoaction(), nbt), getWorld(), getPos(), null);
	}
	
	// ====================Extra====================
	
	public ItemStack getStructureDrop() throws CorruptedConnectionException, NotYetConnectedException {
		if (parent != null) {
			return parent.getStructure().getStructureDrop();
		}
		
		load();
		BlockPos pos = getMinPos(new MutableBlockPos(getPos()));
		
		ItemStack stack = new ItemStack(LittleTiles.multiTiles);
		LittlePreviews previews = getPreviews(pos);
		
		LittlePreview.savePreview(previews, stack);
		
		if (name != null) {
			NBTTagCompound display = new NBTTagCompound();
			display.setString("Name", name);
			stack.getTagCompound().setTag("display", display);
		}
		return stack;
	}
	
	public boolean onBlockActivated(World worldIn, LittleTile tile, BlockPos pos, EntityPlayer playerIn, EnumHand hand, @Nullable ItemStack heldItem, EnumFacing side, float hitX, float hitY, float hitZ, LittleActionActivated action) throws LittleActionException {
		return false;
	}
	
	public boolean isBed(EntityLivingBase player) {
		return false;
	}
	
	public void onEntityCollidedWithBlock(World worldIn, BlockPos pos, Entity entityIn) {
		
	}
	
	public void onUpdatePacketReceived() {
		
	}
	
	public int getLightValue(BlockPos pos) {
		return 0;
	}
	
	public float getExplosionResistance() {
		return 0;
	}
	
	// ====================Active====================
	
	public void tick() {
		
	}
	
	@SideOnly(Side.CLIENT)
	public void renderTick(BlockPos pos, double x, double y, double z, float partialTickTime) {
		
	}
	
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared() {
		return 0;
	}
	
	@SideOnly(Side.CLIENT)
	public AxisAlignedBB getRenderBoundingBox() {
		return null;
	}
	
	@SideOnly(Side.CLIENT)
	public void getRenderingCubes(BlockPos pos, BlockRenderLayer layer, List<LittleRenderBox> cubes) {
		
	}
	
	public void addCollisionBoxes(BlockPos pos, AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes, @Nullable Entity entityIn) {
		
	}
	
	public void neighbourChanged() {
		
	}
	
	// ====================Mods====================
	
	@Deprecated
	public void flipForWarpDrive(LittleGridContext context, Axis axis) {
		List<StructureBlockConnector> newBlocks = new ArrayList<>(blocks.size());
		for (StructureBlockConnector block : blocks)
			newBlocks.add(new StructureBlockConnector(RotationUtils.flip(block.pos, axis)));
		
		blocks.clear();
		blocks.addAll(newBlocks);
		
		for (StructureDirectionalField relative : type.directional)
			relative.flip(relative.get(this), context, axis, context.rotationCenter);
	}
	
	@Deprecated
	public void rotateForWarpDrive(LittleGridContext context, Rotation rotation, int steps) {
		List<StructureBlockConnector> newBlocks = new ArrayList<>(blocks.size());
		for (StructureBlockConnector block : blocks) {
			BlockPos pos = block.pos;
			for (int rotationStep = 0; rotationStep < steps; rotationStep++)
				pos = RotationUtils.rotate(pos, rotation);
			newBlocks.add(new StructureBlockConnector(pos));
		}
		
		blocks.clear();
		blocks.addAll(newBlocks);
		
		for (StructureDirectionalField relative : type.directional)
			relative.rotate(relative.get(this), context, rotation, context.rotationCenter);
	}
	
	public class StructureBlockConnector {
		
		public final BlockPos pos;
		private TileEntityLittleTiles cachedTe;
		
		public StructureBlockConnector(BlockPos pos) {
			this.pos = pos;
		}
		
		public BlockPos getAbsolutePos() {
			return getPos().add(pos);
		}
		
		public TileEntityLittleTiles getTileEntity() throws CorruptedConnectionException, NotYetConnectedException {
			if (cachedTe != null && !cachedTe.isInvalid())
				return cachedTe;
			
			World world = getWorld();
			
			BlockPos absoluteCoord = getAbsolutePos();
			Chunk chunk = world.getChunkFromBlockCoords(absoluteCoord);
			if (WorldUtils.checkIfChunkExists(chunk)) {
				TileEntity te = world.getTileEntity(absoluteCoord);
				if (te instanceof TileEntityLittleTiles)
					return cachedTe = (TileEntityLittleTiles) te;
				else
					throw new MissingBlockException(absoluteCoord);
			} else
				throw new NotYetConnectedException();
		}
		
		public void connect() throws CorruptedConnectionException, NotYetConnectedException {
			TileEntityLittleTiles te = getTileEntity();
			if (!te.hasLoaded())
				throw new NotYetConnectedException();
			IStructureTileList structure = te.getStructure(getIndex());
			if (structure == null)
				throw new MissingStructureException(te.getPos());
		}
		
		public IStructureTileList getList() throws CorruptedConnectionException, NotYetConnectedException {
			TileEntityLittleTiles te = getTileEntity();
			if (!te.hasLoaded())
				throw new NotYetConnectedException();
			IStructureTileList structure = te.getStructure(getIndex());
			if (structure != null)
				return structure;
			throw new MissingStructureException(te.getPos());
		}
		
		public int count() throws CorruptedConnectionException, NotYetConnectedException {
			return getList().size();
		}
		
		public void remove() throws CorruptedConnectionException, NotYetConnectedException {
			getTileEntity().updateTiles((x) -> x.removeStructure(getIndex()));
		}
		
	}
	
}
