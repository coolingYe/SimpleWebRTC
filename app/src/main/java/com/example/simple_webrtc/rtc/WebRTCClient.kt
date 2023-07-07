package com.example.simple_webrtc.rtc

import com.example.gesturelib.Camera2Listener
import com.example.simple_webrtc.MainService
import com.example.simple_webrtc.SocketService
import com.example.simple_webrtc.model.Contact
import com.example.simple_webrtc.utils.Log
import com.example.simple_webrtc.utils.PacketWriter
import com.example.simple_webrtc.utils.Utils
import org.json.JSONException
import org.json.JSONObject
import org.webrtc.*
import java.net.Socket
import java.nio.ByteBuffer

class WebRTCClient : SocketService {
    private lateinit var peerConnection: PeerConnection
    private lateinit var peerConnectionFactory: PeerConnectionFactory

    private lateinit var audioSource: AudioSource
    private lateinit var videoSource: VideoSource
    private lateinit var audioTrack: AudioTrack
    private var videoTrack: VideoTrack ?= null

    private var videoCapture: VideoCapturer? = null
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper
    private lateinit var eglBase: EglBase
    private var dataChannel: DataChannel? = null
    private var offer: String? = null
    private var useFrontFacingCamera = true

    private var playerView: SurfaceViewRenderer? = null
    private var proxyVideoSink = ProxyVideoSink()


    private var isIncomingCall = false
    private val sdpMediaConstraints = MediaConstraints()
    private var callContext: CallContext? = null

    private var binder: MainService.MainBinder? = null
    private var contact: Contact? = null

    constructor(
        binder: MainService.MainBinder,
        commSocket: Socket,
        offer: String
    ) : super(commSocket) {
        this.binder = binder
        Log.d(this, "RTCCall() created for incoming calls")
        this.offer = offer
    }

    constructor(
        contact: Contact
    ) : super(null) {
        this.contact = contact
        Log.d(this, "RTCCall() created for outgoing calls")
    }

    companion object {
        private const val VIDEO_TRACK_ID = "Video1"
        private const val AUDIO_TRACK_ID = "Audio1"

        const val ACTION_TYPE = "ACTION_TYPE"
        const val MESSAGE = "message"
        const val HANGUP = "hangUp"
    }

    fun start(isIncomingCall: Boolean) {
        this.isIncomingCall = isIncomingCall
        initPeer()
        if (!isIncomingCall) {
            getVideoTrack(createVideoCapture())
            getAudioTrack()
        }
        initRTC()
    }

    private fun initPeer() {
        Utils.checkIsOnMainThread()

        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        sdpMediaConstraints.optional.add(
            MediaConstraints.KeyValuePair(
                "offerToReceiveVideo",
                "true"
            )
        )
        sdpMediaConstraints.optional.add(
            MediaConstraints.KeyValuePair(
                "offerToReceiveAudio",
                "true"
            )
        )
        sdpMediaConstraints.optional.add(
            MediaConstraints.KeyValuePair(
                "DtlsSrtpKeyAgreement",
                "true"
            )
        )
    }

    fun setEglBase(eglBase: EglBase) {
        this.eglBase = eglBase
    }

    fun setProxyVideoSink(proxyVideoSink: ProxyVideoSink) {
        this.proxyVideoSink = proxyVideoSink
    }

    fun setPlayerView(playerView: SurfaceViewRenderer) {
        this.playerView = playerView
    }

    fun setCallContext(callContext: CallContext) {
        this.callContext = callContext
    }

    private fun initRTC() {
        Utils.checkIsOnMainThread()
        execute {
            val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
            rtcConfig.apply {
                enableCpuOveruseDetection = false
                sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
            }

            if (isIncomingCall.not()) {
                peerConnection = peerConnectionFactory.createPeerConnection(
                    rtcConfig, object : DefaultObserver() {
                        override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                            super.onIceGatheringChange(iceGatheringState)
                            if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                                createOutgoingCall(
                                    contact!!,
                                    peerConnection.localDescription.description
                                )
                            }
                        }

                        override fun onDataChannel(dataChannel: DataChannel) {
                            super.onDataChannel(dataChannel)
                            this@WebRTCClient.dataChannel = dataChannel
                            this@WebRTCClient.dataChannel!!.registerObserver(dataChannelObserver)
                        }

                        override fun onIceConnectionChange(iceConnectionState: PeerConnection.IceConnectionState) {
                            super.onIceConnectionChange(iceConnectionState)
                            when (iceConnectionState) {
                                PeerConnection.IceConnectionState.CONNECTED,
                                PeerConnection.IceConnectionState.FAILED,
                                PeerConnection.IceConnectionState.DISCONNECTED -> {
                                    closeSocket(commSocket)
                                }
                                else -> return
                            }
                        }

                        override fun onIceCandidate(iceCandidate: IceCandidate) {
                            super.onIceCandidate(iceCandidate)
                            Log.d(this, "onIceCandidate ------> : $iceCandidate")
                        }
                    }
                )!!
            } else {
                peerConnection = peerConnectionFactory.createPeerConnection(
                    rtcConfig,
                    object : DefaultObserver() {
                        override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                            super.onIceGatheringChange(iceGatheringState)
                            if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                                val obj = JSONObject()
                                obj.put("action", "connected")
                                obj.put("answer", peerConnection.localDescription.description)
                                commSocket?.let {
                                    val pw = PacketWriter(it)
                                    pw.writeMessage(obj.toString().toByteArray())
                                } ?: Log.d(this, "Dio------> binder null")
                            }
                        }

                        override fun onAddTrack(
                            rtpReceiver: RtpReceiver,
                            mediaStreams: Array<MediaStream>
                        ) {
                            super.onAddTrack(rtpReceiver, mediaStreams)
                            val track = rtpReceiver.track()
                            if (track is VideoTrack) {
                                track.setEnabled(true)
                                track.addSink { p0 ->
                                    if (p0 != null) {
                                        proxyVideoSink.onFrame(p0)
                                    }
                                }
                            }
                        }

                        override fun onIceCandidate(iceCandidate: IceCandidate) {
                            super.onIceCandidate(iceCandidate)
                            Log.d(this, "onIceCandidate ------> : $iceCandidate")
                        }

                        override fun onDataChannel(dataChannel: DataChannel) {
                            super.onDataChannel(dataChannel)
                            this@WebRTCClient.dataChannel = dataChannel
                            this@WebRTCClient.dataChannel!!.registerObserver(dataChannelObserver)
                        }
                    })!!
            }

            if (isIncomingCall.not()) {
                videoTrack?.setEnabled(true)
                audioTrack.setEnabled(true)
                peerConnection.addTrack(videoTrack)
                peerConnection.addTrack(audioTrack)

                playerView?.setMirror(true)
                videoTrack?.addSink(playerView)

                val init = DataChannel.Init()
                init.ordered = true
                dataChannel = peerConnection.createDataChannel("data", init)
                dataChannel!!.registerObserver(dataChannelObserver)
                peerConnection.createOffer(object : DefaultSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription) {
                        super.onCreateSuccess(sessionDescription)
                        peerConnection.setLocalDescription(DefaultSdpObserver(), sessionDescription)
                    }
                }, sdpMediaConstraints)

            } else {
                peerConnection.setRemoteDescription(object : DefaultSdpObserver() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        peerConnection.createAnswer(object : DefaultSdpObserver() {
                            override fun onCreateSuccess(sessionDescription: SessionDescription) {
                                super.onCreateSuccess(sessionDescription)
                                peerConnection.setLocalDescription(
                                    DefaultSdpObserver(),
                                    sessionDescription
                                )
                            }
                        }, sdpMediaConstraints)
                    }

                }, SessionDescription(SessionDescription.Type.OFFER, offer))
            }
        }
    }

    class ProxyVideoSink : VideoSink {
        private var target: VideoSink? = null
        private var camera2Listener: Camera2Listener? = null

        @Synchronized
        override fun onFrame(frame: VideoFrame) {
            val target = this.target

            if (target == null) {
                Log.d(this, "Dropping frame in proxy because target is null.")
            } else {
                target.onFrame(frame)
//                camera2Listener?.method(NV21ToBitmap(contextMain, createNV21Data(frame.buffer.toI420()), frame.buffer.width, frame.buffer.height))
            }
        }

        @Synchronized
        fun setTarget(target: VideoSink?) {
            this.target = target
        }

        @Synchronized
        fun setCameraCallback(camera2Listener: Camera2Listener) {
            this.camera2Listener = camera2Listener
        }
    }

    val dataChannelObserver = object : DataChannel.Observer {
        override fun onBufferedAmountChange(p0: Long) {}

        override fun onStateChange() {
            val channel = dataChannel
            if (channel == null) {
                Log.d(this, "onStateChange dataChannel: is null")
            } else {
                Log.d(this, "onStateChange dataChannel: ${channel.state()}")
                if (channel.state() == DataChannel.State.OPEN) {
                    Log.d(this, "onStateChange dataChannel: Ready")
                }
            }
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            val data = ByteArray(buffer.data.remaining())
            buffer.data.get(data)
            val s = String(data)
            try {
                Log.d(this, "DataChannel onMessage() message: $s")
                val json = JSONObject(s)
                if (json.has(ACTION_TYPE)) {
                    when (json.getString(ACTION_TYPE)) {
                        MESSAGE -> callContext?.onDataChannelCallback(json.getString(MESSAGE))
                        HANGUP -> {
                            reportStateChange(CallState.ENDED)
                        }
                    }
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

    }

    fun sendOnDataChannel(message: String): Boolean {
        val channel = dataChannel
        if (channel == null) {
            Log.w(this, "setCameraEnabled() dataChannel not set => ignore")
            return false
        }

        if (channel.state() != DataChannel.State.OPEN) {
            Log.w(this, "setCameraEnabled() dataChannel not ready => ignore")
            return false
        }

        try {
            channel.send(
                DataChannel.Buffer(
                    ByteBuffer.wrap(
                        message.toByteArray()
                    ), false
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }

        return true
    }

    fun getFrontCameraEnabled(): Boolean {
        return useFrontFacingCamera
    }

    fun switchCamera() {
        Utils.checkIsOnMainThread()
        if (videoCapture != null) {
            val enumerator = Camera2Enumerator(contextMain);
            val deviceName = enumerator.deviceNames.find { !useFrontFacingCamera == enumerator.isFrontFacing(it) }
            if (deviceName != null) {
                (videoCapture as CameraVideoCapturer).switchCamera(object : CameraVideoCapturer.CameraSwitchHandler{
                    override fun onCameraSwitchDone(p0: Boolean) {
                        useFrontFacingCamera = p0
                    }

                    override fun onCameraSwitchError(p0: String?) {

                    }

                }, deviceName)
            }
        }
    }

    private fun createAudioConstraints(): MediaConstraints? {
        val audioConstraints = MediaConstraints()
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googEchoCancellation",
                "true"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googAutoGainControl",
                "false"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googHighpassFilter",
                "false"
            )
        )
        audioConstraints.mandatory.add(
            MediaConstraints.KeyValuePair(
                "googNoiseSuppression",
                "true"
            )
        )
        return audioConstraints
    }

    private fun setRemoteSdp(sdp: String) {
        peerConnection.let {
            val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            it.setRemoteDescription(DefaultSdpObserver(), remoteSdp)
        }
    }

    private fun getVideoTrack(videoCapture: VideoCapturer?): VideoTrack? {
        this.videoCapture = videoCapture
        videoCapture?.let {videoCap ->
            videoSource = peerConnectionFactory.createVideoSource(videoCap.isScreencast)
            surfaceTextureHelper =
                SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoCap.initialize(surfaceTextureHelper, contextMain, videoSource.capturerObserver)
            videoCap.startCapture(640, 480, 30)
            videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            return videoTrack
        }
        return null
    }

    private fun getAudioTrack(): AudioTrack {
        audioSource = peerConnectionFactory.createAudioSource(createAudioConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource)
        return audioTrack
    }

    private fun createVideoCapture(): VideoCapturer? {
        return if (Camera2Enumerator.isSupported(contextMain)) {
            createCameraCapture(Camera2Enumerator(contextMain))
        } else {
            createCameraCapture(Camera1Enumerator(true))
        }
    }

    private fun createCameraCapture(enumerator: CameraEnumerator): VideoCapturer? {
        val deviceNames = enumerator.deviceNames
        for (deviceName in deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val videoCapture: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapture != null) {
                    return videoCapture
                }
            }
        }

        for (deviceName in deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val videoCapture: VideoCapturer? = enumerator.createCapturer(deviceName, null)
                if (videoCapture != null) {
                    return videoCapture
                }
            }
        }
        return null
    }

    fun hangup() {
        Utils.checkIsOnMainThread()

        execute {
            val json = JSONObject().apply {
                put(ACTION_TYPE, HANGUP)
            }
            if (sendOnDataChannel(json.toString())) {
                reportStateChange(CallState.ENDED)
            } else reportStateChange(CallState.ERROR_COMMUNICATION)
        }
    }

    fun cleanup() {
        Log.d(this, "cleanup()")
        execute {
            try {
                peerConnection.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Log.d(this, "cleanup() executor end")
        }

        super.cleanupRTCPeerConnection()

        Log.d(this, "cleanup() done")
    }

    fun releaseCamera() {
        Log.d(this, "releaseCamera()")
        Utils.checkIsOnMainThread()

        try {
            videoCapture?.stopCapture()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun handleAnswer(remoteDesc: String) {
        execute {
            setRemoteSdp(remoteDesc)
        }
    }

    override fun reportStateChange(state: CallState) {
        callContext?.onStateChange(state)
    }

    interface CallContext {
        fun onDataChannelCallback(message: String)
        fun onStateChange(state: CallState)
    }
}