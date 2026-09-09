package com.limelight.utils;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.limelight.AppView;
import com.limelight.Game;
import com.limelight.R;
import com.limelight.ShortcutTrampoline;
import com.limelight.StreamReqBean;
import com.limelight.binding.PlatformBinding;
import com.limelight.computers.ComputerManagerService;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.HostHttpResponseException;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.NvHTTP;
import com.limelight.nvstream.jni.MoonBridge;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;

public class ServerHelper {
    public static final String CONNECTION_TEST_SERVER = "android.conntest.moonlight-stream.org";

    public static ComputerDetails.AddressTuple getCurrentAddressFromComputer(ComputerDetails computer) throws IOException {
        if (computer.activeAddress == null) {
            throw new IOException("No active address for "+computer.name);
        }
        return computer.activeAddress;
    }

    public static Intent createPcShortcutIntent(Activity parent, ComputerDetails computer) {
        Intent i = new Intent(parent, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.setAction(Intent.ACTION_DEFAULT);
        return i;
    }

    public static Intent createAppShortcutIntent(Activity parent, ComputerDetails computer, NvApp app) {
        Intent i = new Intent(parent, ShortcutTrampoline.class);
        i.putExtra(AppView.NAME_EXTRA, computer.name);
        i.putExtra(AppView.UUID_EXTRA, computer.uuid);
        i.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        i.putExtra(Game.EXTRA_APP_ID, ""+app.getAppId());
        i.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        i.setAction(Intent.ACTION_DEFAULT);
        return i;
    }

    public static Intent createStartIntent(Activity parent, NvApp app, ComputerDetails computer,
                                           ComputerManagerService.ComputerManagerBinder managerBinder) {

        Intent intent = new Intent(parent, Game.class);
        intent.putExtra(Game.EXTRA_HOST, computer.activeAddress.address);
        intent.putExtra(Game.EXTRA_PORT, computer.activeAddress.port);
        intent.putExtra(Game.EXTRA_HTTPS_PORT, computer.httpsPort);
        intent.putExtra(Game.EXTRA_APP_NAME, app.getAppName());
        intent.putExtra(Game.EXTRA_APP_ID, app.getAppId());
        intent.putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported());
        intent.putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId());
        intent.putExtra(Game.EXTRA_PC_UUID, computer.uuid);
        intent.putExtra(Game.EXTRA_PC_NAME, computer.name);
        try {
            if (computer.serverCert != null) {
                intent.putExtra(Game.EXTRA_SERVER_CERT, computer.serverCert.getEncoded());
            }
        } catch (CertificateEncodingException e) {
            e.printStackTrace();
        }
        return intent;
    }

    public static void doStart(Activity parent, NvApp app, ComputerDetails computer,
                               ComputerManagerService.ComputerManagerBinder managerBinder) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(parent, parent.getResources().getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show();
            return;
        }
        parent.startActivity(createStartIntent(parent, app, computer, managerBinder));
    }

    public static void doNetworkTest(final Activity parent) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                SpinnerDialog spinnerDialog = SpinnerDialog.displayDialog(parent,
                        parent.getResources().getString(R.string.nettest_title_waiting),
                        parent.getResources().getString(R.string.nettest_text_waiting),
                        false);

                int ret = MoonBridge.testClientConnectivity(CONNECTION_TEST_SERVER, 443, MoonBridge.ML_PORT_FLAG_ALL);
                UdpNetworkProbe.Result udpProbeResult = UdpNetworkProbe.run(CONNECTION_TEST_SERVER);

                // A successful quality probe is stronger evidence than the single-response
                // connectivity check, which may have resolved a different test server address.
                if (ret != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE &&
                        udpProbeResult.status == UdpNetworkProbe.Status.SUCCESS) {
                    ret &= ~MoonBridge.ML_PORT_FLAG_UDP_47998;
                }
                spinnerDialog.dismiss();

                String dialogSummary;
                if (ret == MoonBridge.ML_TEST_RESULT_INCONCLUSIVE) {
                    dialogSummary = parent.getResources().getString(
                            udpProbeResult.status == UdpNetworkProbe.Status.SUCCESS
                                    ? R.string.nettest_port_check_inconclusive
                                    : R.string.nettest_text_inconclusive);
                }
                else if (ret == 0) {
                    dialogSummary = parent.getResources().getString(R.string.nettest_text_success);
                }
                else {
                    dialogSummary = parent.getResources().getString(R.string.nettest_text_failure);
                    dialogSummary += MoonBridge.stringifyPortFlags(ret, "\n");
                }

                dialogSummary += "\n\n" + parent.getResources().getString(R.string.nettest_udp_probe_heading);
                if (udpProbeResult.status == UdpNetworkProbe.Status.SUCCESS) {
                    dialogSummary += "\n" + parent.getResources().getString(
                            R.string.nettest_udp_probe_result,
                            udpProbeResult.receivedPackets,
                            udpProbeResult.sentPackets,
                            udpProbeResult.packetLossPercent,
                            udpProbeResult.averageRttMs,
                            udpProbeResult.jitterMs);

                    if (udpProbeResult.packetLossPercent == 0) {
                        dialogSummary += "\n" + parent.getResources().getString(R.string.nettest_udp_probe_good);
                    }
                    else if (udpProbeResult.packetLossPercent <= 1.0f) {
                        dialogSummary += "\n" + parent.getResources().getString(R.string.nettest_udp_probe_minor_loss);
                    }
                    else {
                        dialogSummary += "\n" + parent.getResources().getString(R.string.nettest_udp_probe_poor);
                    }
                }
                else if (ret != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE &&
                        (ret & MoonBridge.ML_PORT_FLAG_UDP_47998) != 0) {
                    dialogSummary += "\n" + parent.getResources().getString(R.string.nettest_udp_probe_blocked);
                }
                else {
                    dialogSummary += "\n" + parent.getResources().getString(R.string.nettest_udp_probe_unavailable);
                }
                dialogSummary += "\n\n" + parent.getResources().getString(R.string.nettest_udp_probe_scope);

                Dialog.displayDialog(parent,
                        parent.getResources().getString(R.string.nettest_title_done),
                        dialogSummary,
                        false);
            }
        }, "NetworkTest").start();
    }

    public static String quitHostKey(X509Certificate cert, ComputerDetails.AddressTuple address) {
        // Certificate identity survives switching between LAN and Tailscale addresses.
        return cert != null ? java.util.Arrays.toString(cert.getPublicKey().getEncoded())
                : address.address + ":" + address.port;
    }

    private interface QuitTransport { NvHTTP create() throws IOException; }

    private static void quitInBackground(Activity parent, String key, QuitTransport transport,
                                         Runnable onComplete) {
        android.content.Context app = parent.getApplicationContext();
        com.limelight.nvstream.QuitTracker.Ticket ticket = com.limelight.nvstream.QuitTracker.begin(key);
        if (ticket == null) {
            if (onComplete != null) onComplete.run();
            Toast.makeText(app, "正在结束上次串流，请稍候…", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            String failure = null;
            try {
                if (!transport.create().quitApp()) failure = "主机拒绝退出，请在电脑上检查当前会话。";
            } catch (IOException | XmlPullParserException e) {
                failure = "未能确认串流退出：" + e.getMessage();
            } finally {
                com.limelight.nvstream.QuitTracker.finish(key, ticket, failure);
                if (onComplete != null) onComplete.run();
            }
            if (failure != null) {
                final String message = failure;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                        Toast.makeText(app, message, Toast.LENGTH_LONG).show());
            }
        }, "HostQuitConfirmation").start();
    }

    public static void doQuit(final Activity parent,
                              final ComputerDetails computer,
                              final NvApp app,
                              final ComputerManagerService.ComputerManagerBinder managerBinder,
                              final Runnable onComplete) {
        if (computer.activeAddress == null) {
            Toast.makeText(parent, R.string.pair_pc_offline, Toast.LENGTH_SHORT).show();
            if (onComplete != null) onComplete.run();
            return;
        }
        final ComputerDetails.AddressTuple address = computer.activeAddress;
        final int port = computer.httpsPort;
        final X509Certificate cert = computer.serverCert;
        final String uniqueId = managerBinder.getUniqueId();
        final com.limelight.nvstream.http.LimelightCryptoProvider crypto =
                PlatformBinding.getCryptoProvider(parent.getApplicationContext());
        quitInBackground(parent, quitHostKey(cert, address),
                () -> new NvHTTP(address, port, uniqueId, cert, crypto), onComplete);
    }

    public static void doQuit(final Activity parent, final StreamReqBean reqBean,
                              final Runnable onComplete) {
        final com.limelight.nvstream.http.LimelightCryptoProvider crypto =
                PlatformBinding.getCryptoProvider(parent.getApplicationContext());
        quitInBackground(parent, quitHostKey(reqBean.getServerCert(), reqBean.getActiveAddress()),
                () -> new NvHTTP(reqBean.getActiveAddress(), reqBean.getHttpsPort(),
                        reqBean.getUniqueId(), reqBean.getServerCert(), crypto), onComplete);
    }
}
