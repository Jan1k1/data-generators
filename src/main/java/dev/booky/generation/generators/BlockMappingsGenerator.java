package dev.booky.generation.generators;
// Created by booky10 in MinecraftSource (19:02 05.09.23)

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.booky.generation.util.GenerationUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class BlockMappingsGenerator implements IGenerator {

    @Override
    public void generate(Path outDir, String genName) throws IOException {
        JsonArray array = new JsonArray();
        for (Block block : BuiltInRegistries.BLOCK) {
            List<BlockState> states = block.getStateDefinition().getPossibleStates();
            int defaultIndex = states.indexOf(block.defaultBlockState());

            JsonArray entries = new JsonArray();
            for (BlockState state : states) {
                JsonObject object = new JsonObject();
                state.getValues().forEach(v -> {
                    String valueStr = v.valueName();
                    JsonPrimitive value = NumberUtils.isDigits(valueStr)
                            ? new JsonPrimitive(Integer.parseInt(valueStr))
                            : "true".equals(valueStr) ? new JsonPrimitive(true)
                            : "false".equals(valueStr) ? new JsonPrimitive(false)
                            : new JsonPrimitive(valueStr);
                    object.add(v.property().getName(), value);
                });
                entries.add(object);
            }

            JsonObject object = new JsonObject();
            object.addProperty("type", BuiltInRegistries.BLOCK.getKey(block).getPath());
            object.addProperty("def", defaultIndex);
            object.add("entries", entries);
            array.add(object);
        }

        GenerationUtil.saveJsonElement(array, outDir.resolve(genName + ".json"));
    }
}
