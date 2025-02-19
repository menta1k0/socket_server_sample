package socket_server_sample;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * ソケットサーバのサンプル
 *
 * @author S.Shinohara
 */
public class SocketServer {
    /** 想定外の例外が発生した場合(ERRORログを出力する場合)の完了コード */
    private static final int EXIT_CODE_ON_ERROR = 20;

    //===================================================================
    // 起動スクリプトの環境変数から設定を行う
    //===================================================================
    /**
     * ロガー<br>
     * <br>
     * 起動スクリプトで指定された環境変数「socket_server.log_dir」のパスに「sys:socket_server.log_name」のファイル名で出力する。<br>
     * パスが指定されていない場合はt直下のlogディレクトリにsocket_server.logというファイル名で出力する。
     */
    private final Logger appLogger;

    /**
     * 受信ポート番号<br>
     * <br>
     * 起動スクリプトで指定された環境変数「socket_server.listen_port_no」のポート番号を使用する。<br>
     * ポート番号が指定されていない場合はデフォルトの60000を使用する。
     */
    private static final int LISTEN_PORT_NO = Integer.parseInt(StringUtils.defaultString(System.getenv("socket_server.listen_port_no"), "60000"));

    /**
     * 処理停止時刻<br>
     * <br>
     * 常駐処理を停止する時刻をHHMM形式で指定する。<br>
     * 起動スクリプトで指定された環境変数「socket_server.stop_time」の値を使用する。<br>
     * 処理停止時刻が指定されていない場合はデフォルトの2200(22時に停止)を使用する。<br>
     * <br>
     * 注意：8時に起動して2時に停止するような0時を跨ぐ制御は考慮していない。
     */
    private static final String STOP_TIME_HHMM = StringUtils.defaultString(System.getenv("socket_server.stop_time"), "2200");

    //===================================================================
    // ログ出力関連
    //===================================================================
    /** ログ出力先ディレクトリ */
    private final String LOG_DIR;

    /** ログファイル名 */
    private final String LOG_NAME;

    /** 起動日時 */
    private final LocalDateTime beginDateTime;

    /** 処理済件数 */
    private long processedCount = 0L;

    //------------------------------------------------------------------------------------------------------------------
    /**
     * デフォルトコンストラクタ
     */
    private SocketServer(){
        // 起動時刻を初期化
        beginDateTime = LocalDateTime.now();

        LOG_DIR  = StringUtils.defaultString(System.getenv("socket_server.log_dir"), "log");
        LOG_NAME = StringUtils.defaultString(System.getenv("socket_server.log_name"), "socket_server.log");

        // ロガーを生成
        File logDir = new File(LOG_DIR);
        if(!logDir.exists()){
            try {
                logDir.mkdir();
            }catch (SecurityException e){
                System.out.println("ログ出力先ディレクトリ(" + LOG_DIR + ")が存在しないため作成を試みましたが失敗しました。");
                e.printStackTrace();
                System.exit(EXIT_CODE_ON_ERROR);
            }
        }
        appLogger = LoggerFactory.getLogger(SocketServer.class);
    }

    /**
     * 開始ログを出力する
     */
    private void writeBeginLog(){
        appLogger.info("@@@@ BEGIN @@@@");

        // 実行前提となる情報をログに出力
        appLogger.info("ログファイル出力ディレクトリ=[" + LOG_DIR + "]");
        appLogger.info("ログファイル名              =[" + LOG_NAME + "]");
        appLogger.info("受付ポート番号              =[" + LISTEN_PORT_NO + "]");
        appLogger.info("処理停止時刻(HHMM)          =[" + STOP_TIME_HHMM + "]");
    }

    /**
     * 終了ログを出力する
     *
     * @param exitCode 完了コード
     */
    private void writeEndLog(int exitCode){
        // 起動から終了までの経過時間を計算
        LocalDateTime endDateTime = LocalDateTime.now();
        Duration du = Duration.between(beginDateTime, endDateTime);
        long durationMilliSec = du.toMillis();

        appLogger.info("@@@@ END @@@@ exit_code=[" + exitCode + "] execution_time(ms)=[" + durationMilliSec + "] processed_count=[" + processedCount + "]");
    }

    //------------------------------------------------------------------------------------------------------------------
    /**
     * ソケットサーバを起動する
     *
     * @param args 引数
     */
    public static void main(String[] args){
        SocketServer server = new SocketServer();
        server.writeBeginLog();

        int exitCode = 0;
        try{
            exitCode = server.run();
        }catch (Exception e){
            server.appLogger.error("想定外の例外が発生しました。", e);
            server.writeEndLog(EXIT_CODE_ON_ERROR);
            System.exit(EXIT_CODE_ON_ERROR);
        }

        server.writeEndLog(exitCode);
    }

    /**
     * 常駐処理を継続してよいかを判定する。<br>
     * <br>
     * 現在時刻が処理停止時刻を超えた場合にfalseを返す。
     *
     * @return 常駐処理の継続可否
     */
    private boolean canBeContinued(){
        SimpleDateFormat sdf = new SimpleDateFormat("HHmm");
        String currentHHmm = sdf.format(new Date());
        return currentHHmm.compareTo(STOP_TIME_HHMM) < 0;
    }

    //------------------------------------------------------------------------------------------------------------------

    /**
     * ソケット通信のリクエストを常駐処理する
     *
     * @return 完了コード
     * @throws IOException ServerSocketのインスタンス生成で想定外が発生した場合
     */
    private int run() throws IOException {
        ServerSocket ss = new ServerSocket(LISTEN_PORT_NO);
        while(canBeContinued()){
            // 接続待機
            Socket socket = ss.accept();

            //---------------------------
            // 接続が来た場合に実行
            //---------------------------
            // メッセージを受信
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line = input.readLine();
            while(line != null){
                sb.append(line);
                line = input.readLine();
            }
            // 改行されている場合は1行として扱う
            String requestedMessage = sb.toString();
            appLogger.info("message received. [" + requestedMessage + "]");

            // メッセージを処理する
            String responseMessage = processMessage(requestedMessage);
            processedCount++;

            // 応答を送信
            PrintWriter output = new PrintWriter(socket.getOutputStream(), true);
            output.println(responseMessage);
            appLogger.info("message send. [" + responseMessage + "]");

            // 接続を終了
            socket.close();
        }

        return 0;
    }

    /**
     * 受信したメッセージを処理する
     *
     * @param message 受信したメッセージ
     * @return 応答メッセージ
     */
    private String processMessage(String message){
        // TODO implement this method!!
        return "something to do";
    }
}
