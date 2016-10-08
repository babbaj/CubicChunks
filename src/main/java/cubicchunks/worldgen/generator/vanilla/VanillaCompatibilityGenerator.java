/*
 *  This file is part of Cubic Chunks Mod, licensed under the MIT License (MIT).
 *
 *  Copyright (c) 2015 contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package cubicchunks.worldgen.generator.vanilla;

import java.util.List;

import cubicchunks.util.Box;
import cubicchunks.util.Coords;
import cubicchunks.world.ICubicWorld;
import cubicchunks.world.column.Column;
import cubicchunks.world.cube.Cube;
import cubicchunks.worldgen.generator.CubePrimer;
import cubicchunks.worldgen.generator.ICubeGenerator;
import cubicchunks.worldgen.generator.ICubePrimer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderHell;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.Biome.SpawnListEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkGenerator;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class VanillaCompatibilityGenerator implements ICubeGenerator {

	private IChunkGenerator vanilla;
	private ICubicWorld world;

	private Chunk lastChunk;
	private boolean optimizationHack;

	private boolean stripBadrock;
	private IBlockState badrock = Blocks.BEDROCK.getDefaultState();
	private IBlockState underBlock = Blocks.STONE.getDefaultState();

	public VanillaCompatibilityGenerator(IChunkGenerator vanilla, ICubicWorld world) {
		this.vanilla = vanilla;
		this.world = world;

		// heuristics TODO: add a config that overrides this
		lastChunk = vanilla.provideChunk(0, 0); // lets scan the chunk at 0, 0

		IBlockState topstate = null;
		int         topcount = 0;
		{   // find the type of block that is most common on the bottom layer
			IBlockState laststate = null;
			for(int at = 0;at < 16 * 16;at++) {
				IBlockState state = lastChunk.getBlockState(at | 0x0F, 0, at >> 4);
				if(state != laststate) {

					int count = 1;
					for(int i = at + 1;i < 16 * 16;i++) {
						if(lastChunk.getBlockState(i | 0x0F, 0, i >> 4) == state) {
							count++;
						}
					}
					if(count > topcount) {
						topcount = count;
						topstate = state;
					}
				}
				laststate = state;
			}
		}

		if(topstate.getBlock() != Blocks.BEDROCK) {
			underBlock = topstate;
		}else{
			stripBadrock = true;
			underBlock = world.getProvider() instanceof WorldProviderHell
					? Blocks.NETHERRACK.getDefaultState()
					: Blocks.STONE.getDefaultState(); //TODO: maybe scan for stone type?
		}
	}

	private Biome[] biomes;
	@Override
	public void generateColumn(Column column) {

		this.biomes = this.world.getBiomeProvider()
				.getBiomes(this.biomes, 
						Coords.cubeToMinBlock(column.getX()),
						Coords.cubeToMinBlock(column.getZ()),
						Coords.CUBE_MAX_X, Coords.CUBE_MAX_Z);

		byte[] abyte = column.getBiomeArray();
		for (int i = 0; i < abyte.length; ++i) {
			abyte[i] = (byte)Biome.getIdForBiome(this.biomes[i]);
		}
	}

	@Override
	public void recreateStructures(Column column) {
		vanilla.recreateStructures(column, column.getX(), column.getZ());
	}

	@Override
	public ICubePrimer generateCube(int cubeX, int cubeY, int cubeZ) {
		CubePrimer primer = new CubePrimer();

		if(cubeY < 0) {
			for(int x = 0;x < Coords.CUBE_MAX_X;x++) {
				for(int y = 0;y < Coords.CUBE_MAX_Y;y++) {
					for(int z = 0;z < Coords.CUBE_MAX_Z;z++) {
						primer.setBlockState(x, y, z, underBlock);
					}
				}
			}
		}else if(cubeY > 15) {
			// over block?
		}else{
			if(lastChunk.xPosition != cubeX || lastChunk.zPosition != cubeZ) {
				lastChunk = vanilla.provideChunk(cubeX, cubeZ);
			}

			//generate 16 cubes at once!
			if(!optimizationHack) {
				optimizationHack = true;
				for(int y = 15; y >= 0; y--) {
					if(y == cubeY) {
						continue;
					}
					world.getCubeFromCubeCoords(cubeX, y, cubeZ);
				}
				optimizationHack = false;
			}

			ExtendedBlockStorage storage = lastChunk.getBlockStorageArray()[cubeY];
			if(storage != null && !storage.isEmpty()) {
				for(int x = 0;x < Coords.CUBE_MAX_X;x++) {
					for(int y = 0;y < Coords.CUBE_MAX_Y;y++) {
						for(int z = 0;z < Coords.CUBE_MAX_Z;z++) {
							IBlockState state = storage.get(x, y, z);
							primer.setBlockState(x, y, z, 
									stripBadrock && state == badrock ? underBlock : state);
						}
					}
				}
			}
		}

		return primer;
	}

	@Override
	public void populate(Cube cube) {
		if(cube.getY() >= 0 && cube.getY() <= 15) {
			for(int x = 0;x < 2;x++) {
				for(int z = 0;z < 2;z++) {
					for(int y = 15; y >= 0;y--) {
						// Vanilla populators break the rules! They need to find the ground!
						world.getCubeFromCubeCoords(cube.getX() + x, y, cube.getZ() + z);
					}
				}
			}
			for(int y = 15; y >= 0;y--) {
				// normal populators would not do this... but we are populating more than one cube!
				world.getCubeFromCubeCoords(cube.getX(), y, cube.getZ()).setPopulated(true);
			}

			vanilla.populate(cube.getX(), cube.getZ()); // ez! >:D
			GameRegistry.generateWorld(cube.getX(), cube.getZ(), (World)world, vanilla, ((World)world).getChunkProvider());
		}
	}

	@Override
	public Box getPopulationRequirement(Cube cube) {
		if(cube.getY() >= 0 && cube.getY() <= 15) {
			return new Box(
					-1,  0 - cube.getY(), -1,
					 0, 15 - cube.getY(),  0
			);
		}
		return NO_POPULATOR_REQUIREMENT;
	}

	@Override
	public void recreateStructures(Cube cube) {}

	@Override
	public List<SpawnListEntry> getPossibleCreatures(EnumCreatureType creatureType, BlockPos pos) {
		return vanilla.getPossibleCreatures(creatureType, pos);
	}

	@Override
	public BlockPos getClosestStructure(String name, BlockPos pos) {
		return vanilla.getStrongholdGen((World)world, name, pos);
	}

}
