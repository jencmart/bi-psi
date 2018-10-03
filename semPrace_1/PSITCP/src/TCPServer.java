import java.net.*;
import java.io.*;

public class TCPServer {
    public static void main(String args[]) {

        try {
            ServerSocket serverSocket = new ServerSocket(12345);
            System.out.println("Server is UP!");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                NewConnection connection = new NewConnection(clientSocket);
            }

        } catch (IOException e) {
            System.out.println("Connection lost: " + e.getMessage());
        }
    }
}

