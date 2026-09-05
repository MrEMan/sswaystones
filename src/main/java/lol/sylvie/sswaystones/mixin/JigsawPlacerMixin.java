/*
  This file is licensed under the MIT License!
  https://github.com/sylvxa/sswaystones/blob/main/LICENSE
*/

package lol.sylvie.sswaystones.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import java.util.ArrayList;
import java.util.List;

import lol.sylvie.sswaystones.Waystones;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.JigsawPlacement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(JigsawPlacement.Placer.class)
public class JigsawPlacerMixin {

    @Unique
    private boolean sswaystones$hasPlacedWaystone = false;

    @Unique
    private boolean sswaystones$isWaystoneElement(
            StructurePoolElement element
    ) {
        return element instanceof SinglePoolElement single
                && single.getTemplateLocation()
                        .getNamespace()
                        .equals(Waystones.MOD_ID)
                && single.getTemplateLocation()
                        .getPath()
                        .endsWith("waystone");
    }

    @WrapOperation(
            method = "tryPlacingChildren",
            at = @At(
                    value = "INVOKE",
                    target =
                            "Lnet/minecraft/world/level/levelgen/structure/" +
                            "pools/StructureTemplatePool;" +
                            "getShuffledTemplates(" +
                            "Lnet/minecraft/util/RandomSource;" +
                            ")" +
                            "Ljava/util/List;"
            )
    )
    private List<StructurePoolElement> sswaystones$limitWaystonesInVillage(
            StructureTemplatePool pool,
            RandomSource randomSource,
            Operation<List<StructurePoolElement>> original
    ) {
        /*
         * Make our own list rather than modifying the list returned by
         * StructureTemplatePool.
         */
        List<StructurePoolElement> result =
                new ArrayList<>(
                        original.call(pool, randomSource)
                );

        if (sswaystones$hasPlacedWaystone) {
            result.removeIf(
                    this::sswaystones$isWaystoneElement
            );
        }

        return result;
    }

    @WrapOperation(
            method = "tryPlacingChildren",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z"
            )
    )
    private boolean sswaystones$markWaystoneInVillagePlaced(
            List<?> list,
            Object object,
            Operation<Boolean> original
    ) {
        if (object instanceof PoolElementStructurePiece piece
                && sswaystones$isWaystoneElement(
                        piece.getElement()
                )) {
            sswaystones$hasPlacedWaystone = true;
        }

        return original.call(list, object);
    }
}
