package com.xieguiawu.roar.asr

/**
 * 语音识别回调。所有回调均在主线程触发（见 [SherpaRecognizer] 的 Handler 投递）。
 */
interface RecognizerListener {

    /** 边说边出的中间结果（同一句可能多次触发，以最后一次为准）。 */
    fun onPartial(text: String)

    /** 一句话结束后的最终结果。 */
    fun onFinal(text: String)

    /** 识别失败（权限、模型未就绪、录音错误等），[message] 为可读描述。 */
    fun onError(message: String)
}
