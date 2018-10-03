import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

enum ORIENTATION {LEFT, RIGHT, UP, DOWN}

class SrvSyntaxErr extends Exception {
}

class SrvLogicErr extends Exception {
}

class SrvLoginFailed extends Exception {
}

class SrvSuc extends Exception {
}

class SrvTimeout extends Exception {
}

class Cords {
    int x, y;

    Cords(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

class Position {
    ORIENTATION orientation;
    Cords cords;
}

public class Main {
    private static final int SOCKET_NUMBER = 12345;

    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(SOCKET_NUMBER);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new NewConnection(clientSocket);
            }
        } catch (IOException ignored) {

        }
    }
}

class NewConnection extends Thread {
    private static final int SERVER_TIMEOUT = 1000;
    private static final int TIMEOUT_RECHARGING = 5000;

    private static final String SERVER_MOVE = "102 MOVE";
    private static final String SERVER_TURN_LEFT = "103 TURN LEFT";
    private static final String SERVER_TURN_RIGHT = "104 TURN RIGHT";
    private static final String SERVER_PICK_UP = "105 GET MESSAGE";
    private static final String SERVER_LOGOUT = "106 LOGOUT";

    private static final String SERVER_LOGIN_FAILED = "300 LOGIN FAILED";
    private static final String SERVER_SYNTAX_ERROR = "301 SYNTAX ERROR";
    private static final String SERVER_LOGIC_ERROR = "302 LOGIC ERROR";

    private static final int MINIMUM_STRING_LENGTH = 12;
    private BufferedReader m_inputBuffer;
    private DataOutputStream m_outputStream;
    private Socket m_socketClient;

    NewConnection(Socket socketClient) throws IOException {

        m_socketClient = socketClient;
        m_socketClient.setSoTimeout(SERVER_TIMEOUT);
        m_inputBuffer = new BufferedReader(new InputStreamReader(this.m_socketClient.getInputStream()));
        m_outputStream = new DataOutputStream(this.m_socketClient.getOutputStream());
        System.out.printf("OPEN . ");
        this.start();
    }

    private void EndConnection() {
        try {
            m_socketClient.close();
            System.out.println("CLOSE");
        } catch (IOException e) {
            System.out.println("IMPOSSIBLE TO CLOSE");
        }
    }

    public void run() {

        System.out.printf("USR:OK . ");
        try {
            Authenticate();
            System.out.printf("A:OK . ");
        } catch (SrvSyntaxErr e) {
            WriteHelper(SERVER_SYNTAX_ERROR);
            System.out.printf("A:FAIL (SYNTAX_ERR) . ");
            EndConnection();
            return;
        } catch (SrvLogicErr e) {
            WriteHelper(SERVER_LOGIC_ERROR);
            System.out.printf("A:FAIL (LOGIC_ERR) . ");
            EndConnection();
            return;
        } catch (SrvLoginFailed e) {
            WriteHelper(SERVER_LOGIN_FAILED);
            System.out.printf("A:FAIL (LOGIN_FAIL) . ");
            EndConnection();
            return;
        } catch (SrvTimeout srvTimeout) {
            System.out.printf("A: TIMEOUT . ");
            EndConnection();
            return;
        }

        try {
            GetMessage();
        } catch (SrvSyntaxErr e) {
            WriteHelper(SERVER_SYNTAX_ERROR);
            System.out.printf("MSG: FAIL (SYNTAX_ERR) . ");
            EndConnection();
        } catch (SrvLogicErr e) {
            WriteHelper(SERVER_LOGIC_ERROR);
            System.out.printf("MSG: FAIL (LOGIC_ERR) . ");
            EndConnection();
        } catch (SrvSuc srvSuc) {
            System.out.printf("MSG: OK . ");
            WriteHelper(SERVER_LOGOUT);
            EndConnection();
        } catch (SrvTimeout e) {
            System.out.printf("MSG: TIMEOUT . ");
            EndConnection();
        }

    }

    private void Authenticate() throws SrvSyntaxErr, SrvLogicErr, SrvLoginFailed, SrvTimeout {
        final String SERVER_OK = "200 OK";
        final int SERVER_KEY = 54621;
        final int CLIENT_KEY = 45328;
        final int MODULO = 65536;

        String clientName = ReadSocketHelper(MINIMUM_STRING_LENGTH);

        int hash = 0;
        for (int i = 0; i < clientName.length(); i++)
            hash += (int) clientName.charAt(i);

        WriteHelper(String.valueOf((((hash * 1000) % MODULO) + SERVER_KEY) % MODULO));

        String clientConfirm = ReadSocketHelper(MINIMUM_STRING_LENGTH);

        if (!clientConfirm.matches("\\d+") || clientConfirm.length() > 5)
            throw new SrvSyntaxErr();

        if (((Integer.parseInt(clientConfirm) + (MODULO - CLIENT_KEY)) % MODULO) != ((hash * 1000) % MODULO))
            throw new SrvLoginFailed();

        WriteHelper(SERVER_OK);
    }

    private void Recharge() throws SrvSyntaxErr, SrvLogicErr, SocketException, SrvTimeout {

        final String CLIENT_FULL_POWER = "FULL POWER";

        m_socketClient.setSoTimeout(TIMEOUT_RECHARGING);

        String clientMessage = ReadSocketHelper(16);

        if (clientMessage.equals(CLIENT_FULL_POWER))
            m_socketClient.setSoTimeout(SERVER_TIMEOUT);
        else
            throw new SrvLogicErr();
    }

    // GET MESSAGE
    private void GetMessage() throws SrvSyntaxErr, SrvLogicErr, SrvSuc, SrvTimeout {
        Position currPos = new Position();

        GetMsgPositionHelper(currPos);

        GetMsgSearchCentreHelper(currPos);

    }

    private void GetMsgSearchCentreHelper(Position currPos) throws SrvLogicErr, SrvSyntaxErr, SrvSuc, SrvTimeout {
        TurnMove(currPos, ORIENTATION.RIGHT);
        boolean right = true;
        for (int i = -2; i <= 2; ++i) {
            ReadMessageHelper();
            if (right) {
                for (int j = -2; j < 2; ++j) {
                    ForwardMove(currPos.cords);
                    ReadMessageHelper();
                }
                TurnMove(currPos, ORIENTATION.UP);
                ForwardMove(currPos.cords);
                TurnMove(currPos, ORIENTATION.LEFT);

            } else {
                for (int j = 2; j > -2; --j) {
                    ForwardMove(currPos.cords);
                    ReadMessageHelper();
                }
                if (i < 2) {
                    TurnMove(currPos, ORIENTATION.UP);
                    ForwardMove(currPos.cords);
                    TurnMove(currPos, ORIENTATION.RIGHT);
                }

            }
            right = !right;
        }
    }

    private void GetMsgPositionHelper(Position currPos) throws SrvSyntaxErr, SrvLogicErr, SrvTimeout {
        currPos.cords = MoveHelper(SERVER_MOVE);
        Cords tmpCords = new Cords(currPos.cords.x, currPos.cords.y);
        ForwardMove(currPos.cords);

        if (tmpCords.x > currPos.cords.x)
            currPos.orientation = ORIENTATION.LEFT;
        else if (tmpCords.x < currPos.cords.x)
            currPos.orientation = ORIENTATION.RIGHT;
        else if (tmpCords.y > currPos.cords.y)
            currPos.orientation = ORIENTATION.DOWN;
        else
            currPos.orientation = ORIENTATION.UP;

        if (currPos.cords.x < -2) {
            TurnMove(currPos, ORIENTATION.RIGHT);
            while (currPos.cords.x != -2)
                ForwardMove(currPos.cords);
        } else if (currPos.cords.x > -2) {
            TurnMove(currPos, ORIENTATION.LEFT);
            while (currPos.cords.x != -2)
                ForwardMove(currPos.cords);
        }

        if (currPos.cords.y < -2) {
            TurnMove(currPos, ORIENTATION.UP);
            while (currPos.cords.y != -2)
                ForwardMove(currPos.cords);
        } else if (currPos.cords.y > -2) {
            TurnMove(currPos, ORIENTATION.DOWN);
            while (currPos.cords.y != -2)
                ForwardMove(currPos.cords);
        }
    }

    // MOVE
    private void ForwardMove(Cords p) throws SrvSyntaxErr, SrvLogicErr, SrvTimeout {
        Cords newCords;

        while (true) {
            newCords = MoveHelper(SERVER_MOVE);
            if (newCords.x != p.x || newCords.y != p.y)
                break;
        }

        p.x = newCords.x;
        p.y = newCords.y;

    }

    private void TurnMove(Position p, ORIENTATION o) throws SrvSyntaxErr, SrvLogicErr, SrvTimeout {
        if (p.orientation == o)
            return;

        if ((o == ORIENTATION.UP && p.orientation == ORIENTATION.RIGHT) ||
                (o == ORIENTATION.RIGHT && p.orientation == ORIENTATION.DOWN) ||
                (o == ORIENTATION.DOWN && p.orientation == ORIENTATION.LEFT) ||
                (o == ORIENTATION.LEFT && p.orientation == ORIENTATION.UP))
            MoveHelper(SERVER_TURN_LEFT);

        else if ((o == ORIENTATION.RIGHT && p.orientation == ORIENTATION.UP) ||
                (o == ORIENTATION.DOWN && p.orientation == ORIENTATION.RIGHT) ||
                (o == ORIENTATION.LEFT && p.orientation == ORIENTATION.DOWN) ||
                (o == ORIENTATION.UP && p.orientation == ORIENTATION.LEFT))
            MoveHelper(SERVER_TURN_RIGHT);
        else {
            MoveHelper(SERVER_TURN_RIGHT);
            MoveHelper(SERVER_TURN_RIGHT);
        }

        p.orientation = o;
    }

    private Cords MoveHelper(String serverDirection) throws SrvLogicErr, SrvSyntaxErr, SrvTimeout {
        WriteHelper(serverDirection);
        String clientMessage = ReadSocketHelper(16);

        int x, y;
        if (clientMessage.matches("OK -?\\d+ -?\\d+")) {
            String[] numbers = clientMessage.substring(3, clientMessage.length()).split(" ");
            x = Integer.valueOf(numbers[0]);
            y = Integer.valueOf(numbers[1]);
        } else
            throw new SrvSyntaxErr();

        return new Cords(x, y);
    }

    // SOCKET I/O
    private String ReadSocketHelper(int maxLen) throws SrvSyntaxErr, SrvLogicErr, SrvTimeout {
        final String CLIENT_RECHARGING = "RECHARGING";
        StringBuilder s = new StringBuilder();
        int val;

        try {
            while (s.length() < maxLen && ((val = m_inputBuffer.read()) != -1)) {
                if (s.append((char) val).length() >= 2) {
                    if (s.length() + 1 == maxLen && s.charAt(s.length() - 1) != '\007')
                        throw new SrvSyntaxErr();

                    if (s.substring(s.length() - 2).equals("\007\b")) {
                        String subtr = s.substring(0, s.length() - 2);

                        if (subtr.equals(CLIENT_RECHARGING)) {
                            Recharge();
                            return ReadSocketHelper(maxLen);
                        }
                        return subtr;
                    }
                }
            }
        } catch (SocketTimeoutException e) {
            throw new SrvTimeout();
        } catch (IOException e) {
            System.out.printf("I_EXCEPTION . ");
        }
        throw new SrvSyntaxErr();

    }

    private void WriteHelper(String s) {
        s += "\007\b";
        try {
            m_outputStream.writeBytes(s);
        } catch (IOException e) {
            System.out.printf("O_EXCEPTION . ");
        }
    }

    private void ReadMessageHelper() throws SrvSyntaxErr, SrvLogicErr, SrvSuc, SrvTimeout {
        WriteHelper(SERVER_PICK_UP);
        String s = ReadSocketHelper(100);
        if (s.length() == 0)
            return;
        throw new SrvSuc();
    }
}
