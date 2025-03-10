package dev.wolo.getaclew

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents
import net.minecraft.block.ShapeContext
import net.minecraft.client.MinecraftClient
import net.minecraft.item.Items
import net.minecraft.particle.ParticleTypes
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import org.slf4j.LoggerFactory

object GetAClewClient : ClientModInitializer {
    private val logger = LoggerFactory.getLogger("get-a-clew")

    private var clewLine = mutableListOf<BlockPos>()
    private var underground = false
    private var lastLitPosition: BlockPos? = null
    private var lastTrivialPosition: BlockPos? = null

    override fun onInitializeClient() {
        logger.debug("Get a Clew is here!")
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            trackLine(client)
            drawLine(client)
        }
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register { client, world ->
            clewLine = mutableListOf()
            underground = false
            lastLitPosition = null
            lastTrivialPosition = null
        }
    }

    private fun trackLine(client: MinecraftClient) {
        val player = client.player ?: return
        val position = player.blockPos
        val skyTrace =
                client.world?.raycast(
                        RaycastContext(
                                Vec3d(position).add(Vec3d(0.5, 2.5, 0.5)),
                                Vec3d(position).add(Vec3d(0.5, 512.5, 0.5)),
                                RaycastContext.ShapeType.COLLIDER,
                                RaycastContext.FluidHandling.NONE,
                                ShapeContext.of(player)
                        )
                )
                        ?: return
        if (skyTrace.type == HitResult.Type.MISS) {
            underground = false
            logger.debug("We're above ground, keep the clew line in sync with our position")
            lastLitPosition = position
            lastTrivialPosition = null
            if (clewLine.isEmpty()) {
                clewLine.add(position)
            } else {
                while (clewLine.size > 1) {
                    clewLine.removeAt(0)
                }
                clewLine[0] = position
            }
            return
        }
        underground = true
        if (clewLine.isEmpty()) {
            logger.debug("We're underground with no clew line, give up")
            return
        }
        logger.debug("Find the last position in the clew line we have a path to")
        for (i in 0 ..< clewLine.size) {
            val clewPosition = clewLine[i]
            if (Vec3d(clewPosition).distanceTo(Vec3d(position)) > 16.0) {
                logger.debug("Too far away, ignore this one")
                continue
            }
            val clewTrace =
                    client.world?.raycast(
                            RaycastContext(
                                    Vec3d(position).add(Vec3d(0.5, 0.5, 0.5)),
                                    Vec3d(clewPosition).add(Vec3d(0.5, 0.5, 0.5)),
                                    RaycastContext.ShapeType.COLLIDER,
                                    RaycastContext.FluidHandling.NONE,
                                    player
                            )
                    )
                            ?: continue
            if (clewTrace.type == HitResult.Type.MISS) {
                logger.debug(
                        "We can see this block! It should be the end of the clew line, and we don't"
                )
                logger.debug("need to add a new position")
                while (clewLine.size > i + 1) {
                    clewLine.removeAt(i + 1)
                }
                lastTrivialPosition = position
                return
            }
        }
        logger.debug(
                "We didn't find any positions where we could see the clew line, so we should add the last trivial position."
        )
        logger.debug("If we don't have one, the clew line is broken, but we'll keep it around.")
        clewLine.add(lastTrivialPosition ?: return)
        logger.debug("Clear last trivial position so we don't get stuck in an infinite loop")
        lastTrivialPosition = null
    }

    private fun drawLine(client: MinecraftClient) {
        if (!underground || clewLine.isEmpty()) {
            logger.debug("Underground: ${underground}, Empty: ${clewLine.isEmpty()}")
            return
        }
        logger.debug("Underground: ${underground}, Clew: ${clewLine}")
        val player = client.player ?: return
        if (player.mainHandStack.item != Items.COMPASS && player.offHandStack.item != Items.COMPASS
        ) {
            logger.debug("No compass")
            return
        }
        val clewPosition = clewLine[clewLine.size - 1]
        val nextPosition = clewLine.getOrElse(clewLine.size - 2) { clewPosition.add(0, 32, 0) }
        logger.debug("${clewPosition}, ${nextPosition}")
        var toNext = Vec3d(nextPosition.subtract(clewPosition))
        toNext = toNext.normalize().multiply(0.5)
        val particlePosition = Vec3d(clewPosition).add(Vec3d(0.5, 0.5, 0.5))
        client.world?.addParticle(
                ParticleTypes.FLAME,
                particlePosition.x,
                particlePosition.y,
                particlePosition.z,
                toNext.x,
                toNext.y,
                toNext.z
        )
    }
}
