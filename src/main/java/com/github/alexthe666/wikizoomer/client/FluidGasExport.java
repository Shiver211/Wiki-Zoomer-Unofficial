package com.github.alexthe666.wikizoomer.client;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@SideOnly(Side.CLIENT)
public final class FluidGasExport {
    private static final Logger LOGGER = LogManager.getLogger();

    private FluidGasExport() {
    }

    public static List<ExportTask> createTasks(Set<String> modIds, float zoomPercent,
            ExportTask.Background background, int exportSize, boolean animatedGif) {
        List<ExportTask> tasks = new ArrayList<>();
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            ResourceLocation id = new ResourceLocation(FluidRegistry.getDefaultFluidName(fluid));
            if (modIds.contains(id.getNamespace())) {
                FluidStack stack = new FluidStack(fluid, Fluid.BUCKET_VOLUME);
                addTask(tasks, ExportTask.Type.FLUID, id, fluid.getStill(stack), fluid.getColor(stack),
                        zoomPercent, background, exportSize, animatedGif);
            }
        }
        if (Loader.isModLoaded("mekanism")) {
            addGasTasks(tasks, modIds, zoomPercent, background, exportSize, animatedGif);
        }
        tasks.sort(Comparator.comparing(task -> task.outputFile.getPath()));
        return tasks;
    }

    private static void addGasTasks(List<ExportTask> tasks, Set<String> modIds, float zoomPercent,
            ExportTask.Background background, int exportSize, boolean animatedGif) {
        // 仅在安装 Mekanism 时读取其稳定 API，避免给本模组增加强制依赖。
        try {
            Class<?> registryClass = Class.forName("mekanism.api.gas.GasRegistry");
            Class<?> gasClass = Class.forName("mekanism.api.gas.Gas");
            Method getName = gasClass.getMethod("getName");
            Method getIcon = gasClass.getMethod("getIcon");
            Method getTint = gasClass.getMethod("getTint");
            Iterable<?> gases = (Iterable<?>) registryClass.getMethod("getRegisteredGasses").invoke(null);
            for (Object gas : gases) {
                String name = (String) getName.invoke(gas);
                ResourceLocation texture = (ResourceLocation) getIcon.invoke(gas);
                if (texture == null) {
                    continue;
                }
                ResourceLocation id = getGasId(name, texture);
                if (modIds.contains(id.getNamespace())) {
                    // Gas 的 tint 是 RGB；Fluid 的 color 才包含透明度。
                    int color = (Integer) getTint.invoke(gas) | 0xFF000000;
                    addTask(tasks, ExportTask.Type.GAS, id, texture, color,
                            zoomPercent, background, exportSize, animatedGif);
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Failed to read Mekanism gas registry for export", e);
        }
    }

    private static ResourceLocation getGasId(String name, ResourceLocation texture) {
        if (name.contains(":")) {
            return new ResourceLocation(name);
        }
        // 旧版 GasRegistry 没有注册者字段，与 JEI 一样使用材质命名空间。
        // 不能使用关联流体的归属，例如 Mekanism 水蒸气关联的是原版水。
        return new ResourceLocation(texture.getNamespace(), name);
    }

    private static void addTask(List<ExportTask> tasks, ExportTask.Type type, ResourceLocation id,
            ResourceLocation texture, int color, float zoomPercent, ExportTask.Background background,
            int exportSize, boolean animatedGif) {
        ExportTask task = ExportManager.createTextureTask(type, id, texture, color,
                zoomPercent, background, exportSize, animatedGif);
        if (task != null) {
            tasks.add(task);
        }
    }
}
