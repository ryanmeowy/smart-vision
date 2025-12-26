package com.smart.vision.core.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ImageUtil {

    /**
     * 压缩图片并统一转为 JPG 格式
     */
    public static byte[] resizeAndConvertToJpg(byte[] originalBytes) {
        try {
            // 1. 读取原始图片 (支持 PNG, JPG, BMP 等)
            ByteArrayInputStream bais = new ByteArrayInputStream(originalBytes);
            BufferedImage src = ImageIO.read(bais);
            if (src == null) return null;

            // 2. 计算目标尺寸 (限制最大边长 1024)
            int maxDim = 1024;
            int width = src.getWidth();
            int height = src.getHeight();
            if (width > maxDim || height > maxDim) {
                float aspect = (float) width / height;
                if (width > height) {
                    width = maxDim;
                    height = (int) (maxDim / aspect);
                } else {
                    height = maxDim;
                    width = (int) (maxDim * aspect);
                }
            }

            // 3. 创建新图片 (RGB模式，去掉 PNG 的透明度)
            BufferedImage dest = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dest.createGraphics();
            // 绘制并缩放
            g.drawImage(src.getScaledInstance(width, height, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();

            // 4. 输出为 JPG 字节流
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(dest, "jpg", baos);
            return baos.toByteArray();

        } catch (Exception e) {
//            log.error("图片压缩失败", e);
            return originalBytes; // 失败了就返原图
        }

    }
}
