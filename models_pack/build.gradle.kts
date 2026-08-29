// Asset pack module for AkAI's ML models (Vosk STT + MediaPipe + TFLite LSTM).
//
// WHY this exists: the models total ~857MB, far over Google Play's ~200MB base-app
// limit. Play Asset Delivery ships them in this separate pack instead.
//
// deliveryType = "install-time": the pack is delivered TOGETHER with the base app
// during the initial install. This keeps AkAI fully OFFLINE after install — the
// models are on the device before the app is ever opened. (The alternatives,
// "fast-follow" and "on-demand", download after install and would weaken the
// offline guarantee, so we do NOT use them.)
plugins {
    id("com.android.asset-pack")
}

assetPack {
    packName.set("models_pack")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}
