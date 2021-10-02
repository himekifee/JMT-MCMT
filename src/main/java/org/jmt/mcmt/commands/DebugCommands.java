package org.jmt.mcmt.commands;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.commands.LocateCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TickingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.StructureFeature;

public class DebugCommands {

	public static LiteralArgumentBuilder<CommandSourceStack> registerDebug(LiteralArgumentBuilder<CommandSourceStack> root) {
		return root.then(Commands.literal("getBlockState")
				.then(Commands.argument("location", Vec3Argument.vec3()).executes(cmdCtx -> {
					Coordinates loc = Vec3Argument.getCoordinates(cmdCtx, "location");
					BlockPos bp = loc.getBlockPos(cmdCtx.getSource());
					ServerLevel sw = cmdCtx.getSource().getLevel();
					BlockState bs = sw.getBlockState(bp);
					TextComponent message = new TextComponent(
							"Block at " + bp + " is " + bs.getBlock().getRegistryName());
					cmdCtx.getSource().sendSuccess(message, true);
					System.out.println(message.toString());
					return 1;
				}))).then(Commands.literal("nbtdump")
						.then(Commands.argument("location", Vec3Argument.vec3()).executes(cmdCtx -> {
							Coordinates loc = Vec3Argument.getCoordinates(cmdCtx, "location");
							BlockPos bp = loc.getBlockPos(cmdCtx.getSource());
							ServerLevel sw = cmdCtx.getSource().getLevel();
							BlockState bs = sw.getBlockState(bp);
							BlockEntity te = sw.getBlockEntity(bp);
							if (te == null) {
								TextComponent message = new TextComponent(
										"Block at " + bp + " is " + bs.getBlock().getRegistryName() + " has no NBT");
								cmdCtx.getSource().sendSuccess(message, true);
							}
							CompoundTag nbt = te.serializeNBT();
							Component itc = NbtUtils.toPrettyComponent(nbt);
							TextComponent message = new TextComponent(
									"Block at " + bp + " is " + bs.getBlock().getRegistryName() + " with TE NBT:");
							cmdCtx.getSource().sendSuccess(message, true);
							cmdCtx.getSource().sendSuccess(itc, true);
							//System.out.println(message.toString());
							return 1;
				}))).then(Commands.literal("tick").requires(cmdSrc -> {
					return cmdSrc.hasPermission(2);
				}).then(Commands.literal("te"))
						.then(Commands.argument("location", Vec3Argument.vec3()).executes(cmdCtx -> {
							Coordinates loc = Vec3Argument.getCoordinates(cmdCtx, "location");
							BlockPos bp = loc.getBlockPos(cmdCtx.getSource());
							ServerLevel sw = cmdCtx.getSource().getLevel();
							BlockEntity te = sw.getBlockEntity(bp);
							if (te instanceof TickingBlockEntity) {
								((TickingBlockEntity) te).tick();
								TextComponent message = new TextComponent(
										"Ticked " + te.getClass().getName() + " at " + bp);
								cmdCtx.getSource().sendSuccess(message, true);
							} else {
								TextComponent message = new TextComponent("No tickable TE at " + bp);
								cmdCtx.getSource().sendFailure(message);
							}
							return 1;
						})))
				.then(Commands.literal("classpathDump").requires(cmdSrc -> {
					return cmdSrc.hasPermission(2);
				}).executes(cmdCtx -> {
					Path base = Paths.get("classpath_dump/");
					try {
						Files.createDirectories(base);
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					// Copypasta from syncfu;
					Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator)).flatMap(path -> {
			        	File file = new File(path);
			        	if (file.isDirectory()) {
			        		return Arrays.stream(file.list((d, n) -> n.endsWith(".jar")));
			        	}
			        	return Arrays.stream(new String[] {path});
			        }).filter(s -> s.endsWith(".jar"))
					.map(Paths::get).forEach(path -> {
			        	Path name = path.getFileName();
			        	try {
							Files.copy(path, Paths.get(base.toString(), name.toString()), StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
					
					
					TextComponent message = new TextComponent("Classpath Dumped to: " + base.toAbsolutePath().toString());
					cmdCtx.getSource().sendSuccess(message, true);
					System.out.println(message.toString());
					return 1;
				}))
				/* 1.16.1 code; AKA the only thing that changed  */
				.then(Commands.literal("test").requires(cmdSrc -> {
					return cmdSrc.hasPermission(2);
				}).then(Commands.literal("structures").executes(cmdCtx -> {
					ServerPlayer p = cmdCtx.getSource().getPlayerOrException();
					BlockPos srcpos = p.blockPosition();
					UUID id = p.getUUID();
					int index = structureIdx.computeIfAbsent(id.toString(), (s) -> new AtomicInteger()).getAndIncrement();
					StructureFeature<?>[] targets = net.minecraftforge.registries.ForgeRegistries.STRUCTURE_FEATURES.getValues().toArray(new StructureFeature<?>[10]);
					StructureFeature<?> target = null;
					if (index >= targets.length) {
						target = targets[0];
						structureIdx.computeIfAbsent(id.toString(), (s) -> new AtomicInteger()).set(0);
					} else {
						target = targets[index];
					}
					BlockPos dst = cmdCtx.getSource().getLevel().findNearestMapFeature(target, srcpos, 100, false);
					if (dst == null) {
						TextComponent message = new TextComponent("Failed locating " + target.getFeatureName() + " from " + srcpos);
						cmdCtx.getSource().sendSuccess(message, true);
						return 1;
					}
					p.teleportTo(dst.getX(), srcpos.getY(), dst.getZ());  
					LocateCommand.showLocateResult(cmdCtx.getSource(), target.getFeatureName(), srcpos, dst, "commands.locate.success");
					return 1;
				})))
				/* */
				/*
				.then(Commands.literal("goinf").requires(cmdSrc -> {
					return cmdSrc.hasPermissionLevel(2);
				}).executes(cmdCtx -> {
					ServerPlayerEntity p = cmdCtx.getSource().asPlayer();
					p.setPosition(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
					return 1;
				}))
				*/
				;
	}
	
	private static Map<String, AtomicInteger> structureIdx = new ConcurrentHashMap<>();
}
