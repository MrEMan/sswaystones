/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/

package lol.sylvie.sswaystones.worldgen;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.List;

import lol.sylvie.sswaystones.Waystones;
import lol.sylvie.sswaystones.mixin.StructureTemplatePoolAccessor;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;

public final class VillageInjector {

    private static final ResourceKey<StructureProcessorList> EMPTY_PROCESSOR_LIST_KEY =
            ResourceKey.create(
                    Registries.PROCESSOR_LIST,
                    Identifier.withDefaultNamespace("empty")
            );

    private static final String WAYSTONE_PATH = "waystone";

    private VillageInjector() {
    }

    public static void addBuildingToPool(
            Registry<StructureTemplatePool> templatePoolRegistry,
            Registry<StructureProcessorList> processorListRegistry,
            Identifier poolId,
            String structureId,
            int weight
    ) {
        StructureTemplatePool pool = templatePoolRegistry.getValue(poolId);

        if (pool == null) {
            Waystones.LOGGER.debug(
                    "Could not find structure pool {}",
                    poolId
            );
            return;
        }

        StructureTemplatePoolAccessor accessor =
                (StructureTemplatePoolAccessor) pool;

        /*
         * Do not inject the same Waystone twice if another mod/datapack
         * has already added it to this pool.
         */
        if (accessor.getRawTemplates().stream().anyMatch(entry ->
                isWaystoneElement(entry.getFirst()))) {
            return;
        }

        Holder<StructureProcessorList> processorList =
                processorListRegistry.getOrThrow(
                        EMPTY_PROCESSOR_LIST_KEY
                );

        SinglePoolElement piece =
                SinglePoolElement
                        .single(structureId, processorList)
                        .apply(StructureTemplatePool.Projection.RIGID);

        /*
         * Keep both representations synchronized.
         *
         * StructureTemplatePool uses the flattened `templates` list for
         * random selection while rawTemplates retains element/weight pairs.
         */
        for (int i = 0; i < weight; i++) {
            accessor.getTemplates().add(piece);
        }

        List<Pair<StructurePoolElement, Integer>> rawTemplates =
                new ArrayList<>(accessor.getRawTemplates());

        rawTemplates.add(Pair.of(piece, weight));

        accessor.setRawTemplates(rawTemplates);

        Waystones.LOGGER.debug(
                "Injected Waystone {} into village pool {} with weight {}",
                structureId,
                poolId,
                weight
        );
    }

    private static boolean isWaystoneElement(
            StructurePoolElement element
    ) {
        if (!(element instanceof SinglePoolElement single)) {
            return false;
        }

        Identifier id = single.getTemplateLocation();

        return id.getNamespace().equals(Waystones.MOD_ID)
                && id.getPath().endsWith(WAYSTONE_PATH);
    }

    /**
     * Returns true for the pool naming conventions used by vanilla and
     * common village-overhaul mods such as CTOV.
     *
     * Vanilla:
     *   minecraft:village/plains/terminators
     *
     * CTOV:
     *   ctov:village/plains/house
     */
/*
    private static boolean isVillageTerminalPool(
            Identifier id
    ) {
        String path = id.getPath();

        if (!(
                path.startsWith("village/")
                        || path.contains("/village/")
                        || path.startsWith("villages/")
                        || path.contains("/villages/")
        )) {
            return false;
        }

        return path.endsWith("/terminators")
                || path.endsWith("/terminator")
                || path.endsWith("/house")
                || path.endsWith("/houses");
    }
*/
    private static boolean isVillageTerminalPool(Identifier id) {
        String path = id.getPath();

        if (!(
                path.startsWith("village/")
                        || path.contains("/village/")
                        || path.startsWith("villages/")
                        || path.contains("/villages/")
        )) {
            return false;
        }

        return path.endsWith("/terminators")
                || path.endsWith("/terminator");
    }

    /*
    private static List<Identifier> findVillagePools(
            Registry<StructureTemplatePool> registry
    ) {
        List<Identifier> result = new ArrayList<>();

        for (Identifier id : registry.keySet()) {
            if (isVillageTerminalPool(id)) {
                result.add(id);
            }
        }

        return result;
    }
*/
    private static List<Identifier> findVillagePools(
            Registry<StructureTemplatePool> registry
    ) {
        List<Identifier> result = new ArrayList<>();

        for (Identifier id : registry.keySet()) {
            String path = id.getPath();

            if (path.contains("village")) {
                Waystones.LOGGER.info(
                        "Village-related template pool: {}",
                        id
                );
            }

            if (isVillageTerminalPool(id)) {
                result.add(id);
            }
        }

        return result;
    }

    /**
     * Selects a Waystone structure appropriate for a vanilla biome pool.
     * Modded villages use the generic structure unless their pool name
     * clearly identifies one of the vanilla biome types.
     */
    private static String getWaystoneStructure(
            Identifier poolId
    ) {
        String path = poolId.getPath();

        if (path.contains("/desert/")) {
            return Waystones.id(
                    "village/desert/waystone"
            ).toString();
        }

        if (path.contains("/savanna/")) {
            return Waystones.id(
                    "village/savanna/waystone"
            ).toString();
        }

        if (path.contains("/snowy/")) {
            return Waystones.id(
                    "village/snowy/waystone"
            ).toString();
        }

        if (path.contains("/taiga/")) {
            return Waystones.id(
                    "village/taiga/waystone"
            ).toString();
        }

        if (path.contains("/plains/")) {
            return Waystones.id(
                    "village/plains/waystone"
            ).toString();
        }

        /*
         * For an unknown village mod we currently use the plains structure
         * as the neutral fallback.
         *
         * This should eventually become a dedicated generic structure.
         */
        return Waystones.id(
                "village/plains/waystone"
        ).toString();
    }

    public static void inject(MinecraftServer server) {
        if (!Waystones.configuration.getInstance().injectVillageStructures) {
            return;
        }

        RegistryAccess.Frozen registryAccess =
                server.registryAccess();

        Registry<StructureTemplatePool> templatePoolRegistry =
                registryAccess.lookupOrThrow(
                        Registries.TEMPLATE_POOL
                );

        Registry<StructureProcessorList> processorListRegistry =
                registryAccess.lookupOrThrow(
                        Registries.PROCESSOR_LIST
                );

        List<Identifier> villagePools =
                findVillagePools(templatePoolRegistry);

        Waystones.LOGGER.info(
                "Found {} potential village structure pools",
                villagePools.size()
        );

        for (Identifier pool : villagePools) {
            String structure = getWaystoneStructure(pool);

            Waystones.LOGGER.debug(
                    "Adding Waystone to village pool {} using {}",
                    pool,
                    structure
            );

            addBuildingToPool(
                    templatePoolRegistry,
                    processorListRegistry,
                    pool,
                    structure,
                    4
            );
        }
    }
}
