package cn.blockforge.generated.geargiant1211ngear.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 对附近玩家广播标题/副标题与聊天提示的小工具。 */
public final class AnnounceUtil {

    public static void broadcastTitle(ServerLevel level, Vec3 center, double radius,
                                      Component title, Component subtitle, Component chat) {
        double radiusSq = radius * radius;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level || player.position().distanceToSqr(center) > radiusSq) {
                continue;
            }
            if (title != null) {
                player.connection.send(new ClientboundSetTitleTextPacket(title));
            }
            if (subtitle != null) {
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            }
            if (chat != null) {
                player.sendSystemMessage(chat);
            }
        }
    }

    public static void actionBar(ServerLevel level, Vec3 center, double radius, Component text) {
        double radiusSq = radius * radius;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level && player.position().distanceToSqr(center) <= radiusSq) {
                player.displayClientMessage(text, true);
            }
        }
    }

    private AnnounceUtil() {
    }
}
