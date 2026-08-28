package com.xieguiawu.roar.asr

import com.xieguiawu.roar.core.DialectRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

/**
 * ModelProvider 纯 JVM 测试：sha256 与模型完整性校验不依赖 Android。
 * 依赖 Context 的方法见 [ModelProviderAndroidTest]（Robolectric）。
 * 模型文件元数据取自 [DialectRegistry.CANTONESE]（2026-08-28 多方言化后统一在此）。
 */
class ModelProviderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** 用稀疏文件瞬间创建指定逻辑体积，避免测试真写几百 MB 磁盘。 */
    private fun writeSparse(file: File, sizeBytes: Long) {
        RandomAccessFile(file, "rw").use { it.setLength(sizeBytes) }
    }

    /** 粤语模型 spec（测试基准）。 */
    private val cantoneseModel = DialectRegistry.CANTONESE.model!!

    @Test
    fun sha256_已知向量() {
        val f = tmp.newFile("roar.bin")
        f.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(
            "74f81fe167d99b4cb41d6d0ccda82278caee9f3e2f25d5e5a3936ff3dcec60d0",
            ModelProvider.sha256(f),
        )
    }

    @Test
    fun sha256_同文件两次输出稳定() {
        val f = tmp.newFile("roar.bin")
        f.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(ModelProvider.sha256(f), ModelProvider.sha256(f))
    }

    @Test
    fun sha256_不同内容输出不同() {
        val a = tmp.newFile("a.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val b = tmp.newFile("b.bin").apply { writeBytes(byteArrayOf(9)) }
        assertNotEquals(ModelProvider.sha256(a), ModelProvider.sha256(b))
    }

    @Test
    fun sha256_输出64位小写十六进制() {
        val f = tmp.newFile("r.bin").apply { writeBytes(byteArrayOf(7)) }
        val hash = ModelProvider.sha256(f)
        assertEquals(64, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun isModelComplete_空目录为false() {
        assertFalse(ModelProvider.isModelComplete(tmp.newFolder("empty"), cantoneseModel))
    }

    @Test
    fun isModelComplete_缺任一文件为false() {
        val dir = tmp.newFolder("partial")
        writeSparse(File(dir, "encoder.int8.onnx"), cantoneseModel.files[0].sizeBytes)
        writeSparse(File(dir, "decoder.int8.onnx"), cantoneseModel.files[1].sizeBytes)
        // 缺 tokens.txt
        assertFalse(ModelProvider.isModelComplete(dir, cantoneseModel))
    }

    @Test
    fun isModelComplete_文件齐全且体积一致为true() {
        val dir = tmp.newFolder("complete")
        for (spec in cantoneseModel.files) {
            writeSparse(File(dir, spec.name), spec.sizeBytes)
        }
        assertTrue(ModelProvider.isModelComplete(dir, cantoneseModel))
    }

    @Test
    fun isModelComplete_体积不符为false() {
        val dir = tmp.newFolder("wrong-size")
        for (spec in cantoneseModel.files) {
            writeSparse(File(dir, spec.name), spec.sizeBytes)
        }
        // tokens.txt 体积被篡改为 1 字节 → 完整性校验应失败
        writeSparse(File(dir, "tokens.txt"), 1)
        assertFalse(ModelProvider.isModelComplete(dir, cantoneseModel))
    }

    @Test
    fun modelFiles_总体积超过100MB_应走首次运行下载而非打包assets() {
        assertTrue(cantoneseModel.totalBytes > 100L * 1024 * 1024)
    }

    @Test
    fun modelFiles_sha256元数据均为64位十六进制() {
        for (spec in cantoneseModel.files) {
            assertTrue(
                "${spec.name} 的哈希非法: ${spec.sha256Hex}",
                spec.sha256Hex.matches(Regex("[0-9a-f]{64}")),
            )
            assertTrue("${spec.name} 体积非法", spec.sizeBytes > 0)
        }
    }
}
