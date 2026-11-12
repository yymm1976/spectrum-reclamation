package com.spectrum_reclamation.spectrum_reclamation.trim;

/**
 * 纹饰效果线性叠加计算模型。
 *
 * 采用「基础值 + 每件增量 × 数量」的线性公式，
 * 例如：石英纹饰每件 +2% 近战伤害，穿满 4 件 = 0.02 * 4 = 8%。
 *
 * 使用示例：
 *   TrimCountedValue damage = TrimCountedValue.linear(0.0, 0.02);
 *   double result = damage.calc(count); // count 为身上装备的纹饰件数（0-4）
 *
 * 设计决策：使用 record 天然具备不可变性和 equals/hashCode，
 * 避免被意外修改，同时保持代码简洁。
 *
 * @param base     基础值（无纹饰时的基准，通常为 0）
 * @param perPiece 每件纹饰的增量（如 0.02 表示 +2%）
 */
public record TrimCountedValue(double base, double perPiece) {

    /**
     * 计算给定纹饰件数时的效果值。
     *
     * 公式：base + perPiece × count
     * count 范围通常为 0-4（4 个盔甲槽位）
     *
     * @param count 身上携带该纹饰材料的盔甲件数（0-4）
     * @return 叠加后的最终效果值
     */
    public double calc(int count) {
        return base + perPiece * count;
    }

    /**
     * 静态工厂方法 —— 创建一个线性叠加的 TrimCountedValue。
     *
     * @param base     基础值（穿 0 件时的值，通常为 0）
     * @param perPiece 每件纹饰的增量
     * @return 新的 TrimCountedValue 实例
     */
    public static TrimCountedValue linear(double base, double perPiece) {
        return new TrimCountedValue(base, perPiece);
    }
}
