package com.example.zombieai.ai;

import com.example.zombieai.config.ZombieAIConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Optional Simple Radio integration for the Forge 1.20.1 API.
 * Reflection is deliberate: Simple Radio remains an optional dependency and this class links no
 * Simple Radio classes at class-load time. The 1.20.1 API exposes RadioManager.speakers(), a holder
 * whose get() returns routers; active RadioSpeaker instances expose getSpeakingTime() and getLocation().
 * Radio voice is treated as a high-priority sound at the radio's physical speaker/router location.
 */
public final class SimpleRadioCompat {
    private static final Map<UUID, Integer> NEXT_PULSE = new HashMap<>();
    private static boolean warned;

    private SimpleRadioCompat() {}

    public static void tick(MinecraftServer server) {
        if (!ZombieAIConfig.SIMPLERADIO_ENABLED.get() || ZombieAIConfig.SIMPLERADIO_RANGE.get() <= 0.0) return;
        try {
            Class<?> apiClass = Class.forName("com.codinglitch.simpleradio.SimpleRadioApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (api == null) return;
            Object holder = api.getClass().getMethod("speakers").invoke(api);
            if (holder == null) return;
            Object entries = holder.getClass().getMethod("get").invoke(holder);
            if (!(entries instanceof List<?> routers)) return;
            int now = server.getTickCount();
            NEXT_PULSE.keySet().removeIf(id -> now - NEXT_PULSE.getOrDefault(id, now) > 200);
            for (Object router : routers) {
                if (router == null || !router.getClass().getName().equals("com.codinglitch.simpleradio.radio.RadioSpeaker")) continue;
                int speaking = ((Number) router.getClass().getMethod("getSpeakingTime").invoke(router)).intValue();
                if (speaking <= 0) continue;
                Object idObject = router.getClass().getMethod("getReference").invoke(router);
                if (!(idObject instanceof UUID id) || now < NEXT_PULSE.getOrDefault(id, -1)) continue;
                Object location = router.getClass().getMethod("getLocation").invoke(router);
                if (location == null) continue;
                Field levelField = location.getClass().getField("level");
                Object levelObject = levelField.get(location);
                if (!(levelObject instanceof ServerLevel level)) continue;
                Method x = location.getClass().getMethod("x");
                Method y = location.getClass().getMethod("y");
                Method z = location.getClass().getMethod("z");
                Vec3 pos = new Vec3(((Number) x.invoke(location)).doubleValue(),
                        ((Number) y.invoke(location)).doubleValue(), ((Number) z.invoke(location)).doubleValue());
                NEXT_PULSE.put(id, now + 10);
                broadcast(level, pos);
            }
        } catch (ClassNotFoundException ignored) {
            // Simple Radio is not installed: the rest of Zombie AI Overhaul works normally.
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            if (!warned) {
                warned = true;
                System.err.println("[Zombie AI Overhaul] Simple Radio integration could not read this Simple Radio API build; radio attraction disabled. " + ex.getClass().getSimpleName());
            }
        }
    }

    private static void broadcast(ServerLevel level, Vec3 pos) {
        double range = ZombieAIConfig.SIMPLERADIO_RANGE.get() * ZombieAIConfig.rainFactor(level);
        if (range <= 0.0) return;
        double jitter = Math.min(1.5, ZombieAIConfig.INVESTIGATE_RADIUS.get());
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(pos, pos).inflate(range), ZombieScale::applies)) {
            if (!zombie.isAlive()) continue;
            double distance = zombie.position().distanceTo(pos);
            if (distance > range) continue;
            double wallsCost = ZombieAIConfig.WALL_ATTENUATION.get();
            int maxWalls = wallsCost <= 0.0 ? Integer.MAX_VALUE : (int) Math.floor((range - distance) / wallsCost);
            int walls = wallsCost <= 0.0 ? 0 : PerceptionUtil.countOccluders(level, zombie.getEyePosition(), pos, maxWalls);
            if (walls > maxWalls) continue;
            PerceptionGoal goal = PerceptionGoal.of(zombie);
            if (goal != null) {
                // Radio output is deliberately high priority; audio amplitude is not exposed by the
                // server-side Simple Radio API, so the radius is the configurable loudness proxy.
                goal.hear(pos, jitter, true, true, range - (distance + walls * wallsCost));
            }
        }
    }
}
