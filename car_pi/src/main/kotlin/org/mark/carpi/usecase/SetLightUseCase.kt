package org.mark.carpi.usecase

import org.mark.carpi.repository.CarRepository

/**
 * 前灯开关（整体开/关，不区分颜色）。
 *
 * 有意沿用原 Android Things 实现 ForwardLightAction：red/green/blue 任一 > 0 即三路全亮（占空比 100），
 * 全 0 则全灭；R/G/B 具体数值被忽略（LightCmd.isEnable，手机端 setEnable 也只发 1/1/1 或 0/0/0）。
 * 车上 BCM22/27/24 为数字输出，"全亮" = 三个引脚都置为有效电平。
 */
class SetLightUseCase(private val repository: CarRepository) {
    operator fun invoke(id: Long, on: Boolean) = repository.light(id, on)
}
