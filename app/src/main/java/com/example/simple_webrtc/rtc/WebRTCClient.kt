package com.example.simple_webrtc.rtc

import com.example.simple_webrtc.SocketService
import com.example.simple_webrtc.utils.Log
import com.example.simple_webrtc.utils.PacketWriter
import org.json.JSONObject
import org.webrtc.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketException


class WebRTCClient: SocketService {
    private lateinit var peerConnection: PeerConnection
    private lateinit var peerConnectionFactory: PeerConnectionFactory

    private lateinit var audioSource: AudioSource
    private lateinit var videoSource: VideoSource
    private lateinit var audioTrack: AudioTrack
    private lateinit var videoTrack: VideoTrack

    private lateinit var videoCapture: VideoCapturer
    private lateinit var surfaceTextureHelper: SurfaceTextureHelper
    private val eglBaseContext = EglBase.create().eglBaseContext
    private var dataChannel: DataChannel? = null
    private var offer: String? = null

    private var localPlayerView: SurfaceViewRenderer? = null
    private var netWorkPlayerView: SurfaceViewRenderer? = null

    private var isIncomingCall = false
    private val sdpMediaConstraints = MediaConstraints()

    constructor(
        commSocket: Socket,
        offer: String
    ) : super(commSocket) {
        Log.d(this, "RTCCall() created for incoming calls")
        this.offer = offer
    }

    constructor(
    ) : super(null) {
        Log.d(this, "RTCCall() created for outgoing calls")
    }

    companion object {
        const val VIDEO_TRACK_ID = "ARDAMSv0"
        const val AUDIO_TRACK_ID = "ARDAMSa0"
    }

    fun start(isIncomingCall: Boolean) {
        this.isIncomingCall = isIncomingCall
        initPeer()
        if (!isIncomingCall) {
            getVideoTrack()
            getAudioTrack()
        }
        initRTC()
    }

    private fun initPeer() {
        val options = PeerConnectionFactory.Options()
        val encoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBaseContext)


        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("offerToReceiveVideo", "true"))
        sdpMediaConstraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "false"))
    }

    fun setLocalPlayerView(localPlayerView: SurfaceViewRenderer) {
        this.localPlayerView = localPlayerView
    }

    fun setNetWorkPlayerView(netWorkPlayerView: SurfaceViewRenderer) {
        this.netWorkPlayerView = netWorkPlayerView
    }

    private fun initRTC() {
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList())
        rtcConfig.apply {
            enableCpuOveruseDetection = false
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_ONCE
        }

        rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN

        netWorkPlayerView?.init(eglBaseContext, null)

        if (isIncomingCall.not()) {
            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig, object : DefaultObserver() {
                    override fun onIceGatheringChange(iceGatheringState: PeerConnection.IceGatheringState) {
                        super.onIceGatheringChange(iceGatheringState)
                        if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {
                            createOutgoingCall(peerConnection.localDescription.description)
                        }
                    }
                }
            )?.apply {
                addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                )
                addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_ONLY)
                )
            }!!
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
                                closeSocket(it)
                            }
                        }
                    }

                    override fun onAddStream(mediaStream: MediaStream) {
                        super.onAddStream(mediaStream)
                        mediaStream.let {
                            if (it.videoTracks.isNotEmpty()) {
                                it.videoTracks.first().addSink(netWorkPlayerView)
                            }
                        }
                    }

                })?.apply {
                addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
                addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
            }!!
        }


        if (!isIncomingCall) {
            videoTrack.setEnabled(true)
            audioTrack.setEnabled(true)
            peerConnection.addTrack(videoTrack)
            peerConnection.addTrack(audioTrack)

            localPlayerView?.setMirror(true)
            localPlayerView?.init(eglBaseContext, null)
            videoTrack.addSink(localPlayerView)

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
                            peerConnection.setLocalDescription(DefaultSdpObserver(), sessionDescription)
                        }

                    }, sdpMediaConstraints)
                }

            }, SessionDescription(SessionDescription.Type.OFFER, offer))
        }
    }

    private fun connectRTC(desc: String) {
        if (isIncomingCall) {

        } else {

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

    fun getIpAddressString(): String? {
        try {
            val enNetI = NetworkInterface
                .getNetworkInterfaces()
            while (enNetI.hasMoreElements()) {
                val netI = enNetI.nextElement()
                val enumIpAddr = netI
                    .inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (inetAddress is Inet4Address && !inetAddress.isLoopbackAddress()) {
                        return inetAddress.getHostAddress()
                    }
                }
            }
        } catch (e: SocketException) {
            e.printStackTrace()
        }
        return "0.0.0.0"
    }

    private fun setRemoteSdp(sdp: String) {
        peerConnection.let {
            val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
            peerConnection.setRemoteDescription(DefaultSdpObserver(), remoteSdp)
        }
    }

    private fun getVideoTrack(): VideoTrack {
        videoCapture = createVideoCapture()!!
        videoSource = peerConnectionFactory.createVideoSource(videoCapture.isScreencast)
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
        videoCapture.initialize(surfaceTextureHelper, contextMain, videoSource.capturerObserver)
        videoCapture.startCapture(640, 480, 30)
        videoTrack = peerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource)
        return videoTrack
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

    fun destroy() {
        localPlayerView?.release()
        netWorkPlayerView?.release()
        if (isIncomingCall) {
            videoCapture.dispose()
            videoTrack.dispose()
        }
        peerConnection.dispose()
        peerConnectionFactory.dispose()
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

    override fun handleAnswer(remoteDesc: String) {
        execute {
            setRemoteSdp(remoteDesc)
        }
    }
}