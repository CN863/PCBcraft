package com.pcbcraft.library;

import com.pcbcraft.PCBCraft;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.AddReloadListenerEvent;

/**
 * 元件库相关事件订阅。
 * <p>
 * 在 GAME 事件总线监听 {@link AddReloadListenerEvent}，注册新的 {@link ComponentLibrary}
 * 到重载流程。{@link ComponentLibrary#apply} 完成后会自行更新静态单例，故此处无需额外处理。
 * </p>
 */
@EventBusSubscriber(modid = PCBCraft.MOD_ID, bus = EventBusSubscriber.Bus.FORGE)
public final class LibraryEvents {

    private LibraryEvents() {
    }

    /**
     * 资源重载事件：注册元件库重载监听器。
     * <p>
     * Forge 1.20.1 的 {@link AddReloadListenerEvent#addListener} 接收一个
     * {@code net.minecraft.server.packs.resources.PreparableReloadListener} 实例。
     * {@link ComponentLibrary} 继承 {@code SimpleJsonResourceReloadListener}，进而实现该接口，
     * 故直接传入实例即可（注意：不能传 {@code (stage) -> ...} 形式的 lambda，签名不匹配）。
     * </p>
     *
     * @param event 添加重载监听器事件
     */
    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new ComponentLibrary());
    }
}
