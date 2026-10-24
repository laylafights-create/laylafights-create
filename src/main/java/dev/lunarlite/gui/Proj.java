package dev.lunarlite.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Chiếu toạ độ thế giới ra màn hình GUI (dùng chung cho Bow Trajectory, Block Outline, Enemy ESP).
 * Ở 26.x không còn Tessellator / GL cố định nên các lớp phủ 3D được vẽ 2D trong lớp HUD:
 * mỗi khung hình gọi {@link #setup} một lần, sau đó {@link #project} cho từng điểm.
 * Giới hạn: tính theo mắt camera entity + FOV trong options (bỏ qua hiệu ứng FOV khi chạy / góc nhìn thứ 3).
 */
public final class Proj {
    private Proj() {}

    public static double camX, camY, camZ;
    private static double fx, fy, fz, rx, rz, ux, uy, uz, tanHalf;
    private static float halfH, midX, midY;
    public static boolean ready;

    public static boolean setup(float pt, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        ready = false;
        if (cam == null) return false;
        camX = Mth.lerp(pt, cam.xo, cam.getX());
        camY = Mth.lerp(pt, cam.yo, cam.getY()) + cam.getEyeHeight();
        camZ = Mth.lerp(pt, cam.zo, cam.getZ());
        double yaw = Math.toRadians(cam.getViewYRot(pt)), pit = Math.toRadians(cam.getViewXRot(pt));
        double cy = Math.cos(yaw), sy = Math.sin(yaw), cp = Math.cos(pit), sp = Math.sin(pit);
        fx = -sy * cp; fy = -sp; fz = cy * cp;
        rx = -cy; rz = -sy;
        ux = -rz * fy;
        uy = rz * fx - rx * fz;
        uz = rx * fy;
        tanHalf = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
        halfH = sh / 2f;
        midX = sw / 2f;
        midY = sh / 2f;
        ready = true;
        return true;
    }

    /** {x, y, pixel-mỗi-đơn-vị} hoặc null nếu điểm ở sau camera. */
    public static float[] project(double wx, double wy, double wz) {
        if (!ready) return null;
        double dx = wx - camX, dy = wy - camY, dz = wz - camZ;
        double z = dx * fx + dy * fy + dz * fz;
        if (z < 0.1) return null;
        double x = dx * rx + dz * rz;
        double y = dx * ux + dy * uy + dz * uz;
        float k = (float) (halfH / (z * tanHalf));
        return new float[]{midX + (float) x * k, midY - (float) y * k, k};
    }

    /** Đoạn thẳng thế giới -> màn hình; cắt phần nằm sau camera. Trả về false nếu không vẽ được. */
    public static boolean line(double x0, double y0, double z0, double x1, double y1, double z1, float w, int argb) {
        if (!ready) return false;
        double d0 = (x0 - camX) * fx + (y0 - camY) * fy + (z0 - camZ) * fz;
        double d1 = (x1 - camX) * fx + (y1 - camY) * fy + (z1 - camZ) * fz;
        final double near = 0.12;
        if (d0 < near && d1 < near) return false;
        if (d0 < near) {
            double t = (near - d0) / (d1 - d0);
            x0 += (x1 - x0) * t; y0 += (y1 - y0) * t; z0 += (z1 - z0) * t;
        } else if (d1 < near) {
            double t = (near - d1) / (d0 - d1);
            x1 += (x0 - x1) * t; y1 += (y0 - y1) * t; z1 += (z0 - z1) * t;
        }
        float[] a = project(x0, y0, z0), b = project(x1, y1, z1);
        if (a == null || b == null) return false;
        if (Math.abs(a[0]) > 20000f || Math.abs(a[1]) > 20000f || Math.abs(b[0]) > 20000f || Math.abs(b[1]) > 20000f) return false;
        Draw.line(a[0], a[1], b[0], b[1], w, argb);
        return true;
    }
}
