import java.net.*;
import java.io.*;

class NewConnection extends Thread {

    private BufferedReader input;
    private DataOutputStream output;
    private Socket clientSocket;

    NewConnection(Socket clientSocket) {
        try {
            this.clientSocket = clientSocket;

            input = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            output = new DataOutputStream(this.clientSocket.getOutputStream());

            this.start();

        } catch (IOException e) {
            System.out.println("Connection lost: " + e.getMessage());
        }
    }

    private void LogicError() {
        try {
            output.writeBytes("302 LOGIC ERROR\r\n");
            clientSocket.close();

        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    private StringBuilder RechargeHandler(int type) {
        StringBuilder retChars = new StringBuilder();
        Boolean endStream = false;
        int retChar;

        try {
            clientSocket.setSoTimeout(5000);

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            if (retChars.toString().equals("FULL POWER\r\n") && retChars.length() == 12) {

                retChars = new StringBuilder();
                endStream = false;

                while (true) {

                    retChar = input.read();
                    retChars.append((char) retChar);

                    if (endStream && (char) retChar == '\n') {
                        break;
                    }

                    endStream = ((char) retChar == '\r');
                }

                if (CheckCorrection(retChars, type) != null) {
                    clientSocket.setSoTimeout(1000);
                    return retChars;
                }
                clientSocket.setSoTimeout(1000);
            }

        } catch (Exception e) {
            System.out.println(e.toString());
        }

        return null;
    }

    private void SyntaxError() {
        try {
            output.writeBytes("301 SYNTAX ERROR\r\n");
            clientSocket.close();

        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }

    private StringBuilder CheckCorrection(StringBuilder string, int type) {

        if (string.toString().equals("RECHARGING\r\n") && string.length() == 12) {
            StringBuilder tmpString = RechargeHandler(type);
            if (tmpString == null) {
                LogicError();
                return null;
            }
            return tmpString;
        }

        switch (type) {
            case 0: // CLIENT_USER
                if (string.length() > 100) {
                    SyntaxError();
                    return null;
                }
                break;
            case 1: // CLIENT_PASSWORD
                if (string.length() > 7) {
                    SyntaxError();
                    return null;
                }

                String[] pass = string.toString().split(" ");

                if (pass.length != 1) {
                    SyntaxError();
                    return null;
                }

                pass = string.toString().split("\r");

                if (!(pass[0].matches("\\-?\\d+"))) { // Is not a numeric
                    SyntaxError();
                    return null;
                }

                //"[-+]?\\d*\\.?\\d+"

                break;
            case 2: // CLIENT_CONFIRM
                if (string.length() > 12) {
                    SyntaxError();
                    return null;
                }

                String[] parts = string.toString().split(" ");

                if (parts.length != 3) {
                    SyntaxError();
                    return null;
                }

                parts = string.toString().substring(0, string.length() - 2).split(" ");

                if (!(parts[0].equals("OK"))) {
                    SyntaxError();
                    return null;
                }

                if (!(parts[1].matches("\\-?\\d+"))) { // Is not a numeric
                    SyntaxError();
                    return null;
                }

                if (!(parts[2].matches("\\-?\\d+"))) { // Is not a numeric
                    SyntaxError();
                    return null;
                }

                break;
            case 5: // CLIENT_MESSAGE
                if (string.length() > 100) {
                    SyntaxError();
                    return null;
                }
                break;
        }

        return string;
    }

    private Boolean Authentification(String user, String password) {

        int asciiConvert = 0;

        for (int i = 0; i < user.length(); i++) {
            asciiConvert += user.charAt(i);
        }

        return (asciiConvert == Integer.parseInt(password));
    }

    private Boolean TurnBack() {

        StringBuilder retChars = new StringBuilder();
        Boolean endStream = false;
        int retChar;

        try {
            output.writeBytes("104 TURN RIGHT\r\n");

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 2);
            if (retChars == null) {
                return false;
            }

            output.writeBytes("104 TURN RIGHT\r\n");

            retChars = new StringBuilder();
            endStream = false;

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 2);
            if (retChars == null) {
                return false;
            }

        } catch (Exception e) {
            System.out.println(e.toString());
        }

        return true;
    }

    private Boolean TurnRight() {

        StringBuilder retChars = new StringBuilder();
        Boolean endStream = false;
        int retChar;

        try {
            output.writeBytes("104 TURN RIGHT\r\n");

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 2);
            if (retChars == null) {
                return false;
            }

        } catch (Exception e) {
            System.out.println(e.toString());
        }

        return true;
    }

    private Boolean TurnLeft() {

        StringBuilder retChars = new StringBuilder();
        Boolean endStream = false;
        int retChar;

        try {
            output.writeBytes("103 TURN LEFT\r\n");

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 2);
            if (retChars == null) {
                return false;
            }

        } catch (Exception e) {
            System.out.println(e.toString());
        }

        return true;
    }

    private Boolean FindLocation(String[] axis) {

        int vectorX = Integer.parseInt(axis[1]);
        int vectorY = Integer.parseInt(axis[2]);
        StringBuilder retChars = new StringBuilder();
        Boolean endStream = false;

        int tmpX, tmpY, retChar;

        try {
            if (vectorX == 0 && vectorY == 0) {
                return true;
            }

            output.writeBytes("102 MOVE\r\n");

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 2);
            if (retChars == null) {
                return false;
            }

            tmpX = Integer.parseInt(retChars.toString().substring(0, retChars.length() - 2).split(" ")[1]);
            tmpY = Integer.parseInt(retChars.toString().substring(0, retChars.length() - 2).split(" ")[2]);

            if (tmpX == 0 && tmpY == 0) {
                return true;
            }

            if (tmpX == 0) {
                if ((tmpX > vectorX && tmpY > 0) || (tmpX < vectorX && tmpY < 0)) {
                    if (!TurnRight()) {
                        return false;
                    }
                }

                if ((tmpX > vectorX && tmpY < 0) || (tmpX < vectorX && tmpY > 0)) {
                    if (!TurnLeft()) {
                        return false;
                    }
                }
            }

            if (tmpY == 0) {
                if ((tmpY < vectorY && tmpX < 0) || (tmpY > vectorY && tmpX > 0)) {
                    if (!TurnLeft()) {
                        return false;
                    }
                }

                if ((tmpY > vectorY && tmpX < 0) || (tmpY < vectorY && tmpX > 0)) {
                    if (!TurnRight()) {
                        return false;
                    }
                }
            }

            if ((vectorX > tmpX && vectorX < 0) || (vectorX < tmpX && vectorX > 0) || (vectorY > tmpY && vectorY < 0) || (vectorY < tmpY && vectorY > 0)) {
                if (!TurnBack()) {
                    return false;
                }
            }

            FindLocation(retChars.toString().substring(0, retChars.length() - 2).split(" "));

        } catch (Exception e) {
            System.out.println(e.toString());
        }

        return true;
    }

    public void run() {
        String CLIENT_USER, CLIENT_PASSWORD;
        StringBuilder retChars = new StringBuilder();
        Boolean endStream = false;
        int retChar, retCount = 0;

        try {
            clientSocket.setSoTimeout(1000);

            output.writeBytes("100 LOGIN\r\n");

            while (true) {

                retChar = input.read();
                retCount++;
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');

                if (retCount >= 100) {
                    output.writeBytes("301 SYNTAX ERROR\r\n");
                    clientSocket.close();
                    return;
                }
            }

            retChars = CheckCorrection(retChars, 0);
            if (retChars == null) {
                return;
            }

            CLIENT_USER = retChars.toString().substring(0, retChars.length() - 2);
            System.out.printf("Username: ");
            System.out.println(CLIENT_USER);

            output.writeBytes("101 PASSWORD\r\n");
            retChars = new StringBuilder();
            endStream = false;

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 1);
            if (retChars == null) {
                return;
            }

            CLIENT_PASSWORD = retChars.toString().substring(0, retChars.length() - 2);
            System.out.printf("Password: ");
            System.out.println(CLIENT_PASSWORD);

            if (!Authentification(CLIENT_USER, CLIENT_PASSWORD)) {
                output.writeBytes("300 LOGIN FAILED\r\n");
                clientSocket.close();
                return;
            } else {
                output.writeBytes("200 OK\r\n");
            }

            output.writeBytes("102 MOVE\r\n");
            retChars = new StringBuilder();
            endStream = false;

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                } else if (endStream && !((char) retChar == '\n')) {
                    output.writeBytes("301 SYNTAX ERROR\r\n");
                    clientSocket.close();
                    return;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 2);
            if (retChars == null) {
                return;
            }

            System.out.printf("Position: ");
            System.out.println(retChars.toString().substring(0, retChars.length() - 2));

            if (FindLocation(retChars.toString().substring(0, retChars.length() - 2).split(" "))) {
                output.writeBytes("105 GET MESSAGE\r\n");
            } else {
                return;
            }

            retChars = new StringBuilder();
            endStream = false;

            while (true) {

                retChar = input.read();
                retChars.append((char) retChar);

                if (endStream && (char) retChar == '\n') {
                    break;
                }

                endStream = ((char) retChar == '\r');
            }

            retChars = CheckCorrection(retChars, 5);
            if (retChars == null) {
                return;
            }

            System.out.printf("Message: ");
            System.out.println(retChars.toString());
            output.writeBytes("200 OK\r\n");

            clientSocket.close();
        } catch (SocketTimeoutException e) {

            System.out.println("Connection has timed out!");
            try {
                clientSocket.close();
            } catch (Exception e2) {
                System.out.println(e2.toString());
            }
        } catch (Exception e) {

            System.out.println(e.toString());
            try {
                clientSocket.close();
            } catch (Exception e2) {
                System.out.println(e2.toString());
            }
        }
    }
}
