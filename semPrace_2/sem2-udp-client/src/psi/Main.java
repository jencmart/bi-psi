package psi;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

//   USAGE 127.0.0.1 firmware.bin
//   USAGE 127.0.0.1
class LogicException extends Exception{
    private String s;

    int seq;
    int ack;

    LogicException(String s, int seq, int ack) {
        this.s = s;
        this.seq = seq;
        this.ack = ack;
    }

    @Override
    public String toString() {
        return "LogicException{" +
                "info=' " + s + "\' " +
                '}';
    }
}

class ConnectionException extends Exception{}

class MaxSendException extends  Exception{}

class ServerRstException extends  Exception{
}

class IPAddressValidator{

    private Pattern pattern;

    private static final String IPADDRESS_PATTERN =
            "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                    "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";

    IPAddressValidator(){
        pattern = Pattern.compile(IPADDRESS_PATTERN);
    }

    boolean validate(final String ip){
        Matcher matcher = pattern.matcher(ip);
        return matcher.matches();
    }
}

public class Main {

    private static final String downloadedPhotoName = "downloadedPhoto.png";
    public static void main(String[] args) throws IOException {

        IPAddressValidator ipAddressValidator = new IPAddressValidator();

        if(args.length < 1 || args.length > 2 || ! ipAddressValidator.validate(args[0]) )  {
            System.out.println("Usage:\t<server>   //Download photo\n\t<server> <firmware.bin> // upload firmware");
            return;
        }

        Client client;
        try {
            client = new Client(args[0], 4000);
        } catch (UnknownHostException e) {
            System.out.println("Neznamy host");
            e.printStackTrace();
            return;
        } catch (SocketException e) {
            System.out.println("Socket exception");
            e.printStackTrace();
            return;
        }

        if(args.length == 2)
            client.uploadFirmware(args[1]);
        else
            client.downloadPhoto(downloadedPhotoName);
    }
}


class Packet {
    private Header header;
    private byte[] data;

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    Packet(){}

    Packet(Header header, byte[] data) {
        this.header = header;
        this.data = data;
    }

    Header getHeader() {
        return header;
    }

    void setHeader(Header header) {
        this.header = header;
    }

    byte[] getData() {
        return data;
    }

    void setData(byte[] data) {
        this.data = data;
    }

    @Override
    public String toString() {

        StringBuilder s = new StringBuilder();
        String hex =  bytesToHex(data);

        for(int i = 0 ; i < hex.length() ; ++i)
        {
            if(i == 16)
                s.append(" ...");
            if(i > 15 && i < hex.length() - 16)
                continue;
            if(i%2 == 0) s.append(' ').append( hex.charAt(i) );
            else s.append( hex.charAt(i) );
        }

            String identif = "00000000";
            if(header.identif != 0)
                identif = Integer.toHexString(header.identif);
        return  identif +
                "  seq=" + String.format("%-6d", header.seq )  +
                "ack=" + String.format("%-6d", header.ack ) +
                "flags="+ header.flag +
                "  data(" + String.format("%-3d", data.length ) + "):" + s;
    }
}

class Header {
    int identif;
    int seq;
    int ack;
    int flag;

    Header() {
        this.identif = 0;
        this.seq = 0;
        this.ack = 0;
        this.flag = 0;
    }

    Header(int identif, int seq, int ack, int flag) {
        this.identif = identif;
        this.seq = seq;
        this.ack = ack;
        this.flag = flag;
    }
}

class DatagramHelper {
    private InetAddress address;
    private int port;

    DatagramHelper(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    Packet createPacketFromDatagram(DatagramPacket datagramPacket) throws IOException {
        int length = datagramPacket.getLength();
        ByteArrayInputStream bin = new ByteArrayInputStream(datagramPacket.getData());
        DataInputStream dataInputStream = new DataInputStream(bin);

        // create header
        Header header = new Header();
        header.identif = dataInputStream.readInt();
        header.seq = dataInputStream.readShort() & 0xFFFF;
        header.ack = dataInputStream.readShort() & 0xFFFF;
        header.flag = (char)dataInputStream.readByte();

        // create data
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[(length - 9)];

        nRead = dataInputStream.read(data, 0, data.length) ;
            buffer.write(data, 0, nRead);

        buffer.flush();
        byte[] bytes = buffer.toByteArray();


        // create packet
        Packet packet = new Packet();
        packet.setHeader(header);
        packet.setData(bytes);
        return packet;

    }

    DatagramPacket createDatagramFromPacket(Packet packet) {

        ByteBuffer buff = ByteBuffer.allocate(9+packet.getData().length).
                putInt(packet.getHeader().identif).
                putShort((short) packet.getHeader().seq).
                putShort((short) packet.getHeader().ack).
                put((byte) packet.getHeader().flag);

        for (byte b: packet.getData())
            buff.put(b);

        byte [] bytes = buff.array();

        return new DatagramPacket(bytes, bytes.length, address, port);
    }
}

class Client {

    private DatagramSocket  socket;
    private DatagramHelper datagramHelper;
    private static final byte[] DOWNLOAD = {1};
    private static final byte[] UPLOAD = {2};
    private static final int S = 4;
    private static final int F = 2;
    private static final int R = 1;
    private static final int MAX_PACKET_LENGTH = 264;
    private static final byte[] emptyData = {};

    private int identificator;

    Client(String ipv4address, int port) throws UnknownHostException, SocketException {
        socket = new DatagramSocket();
        datagramHelper = new DatagramHelper(InetAddress.getByName(ipv4address), port);
    }

    private boolean validateConnectionPacket(Packet requestPacket, Packet responsePacket) {

        if(responsePacket.getHeader().identif == 0)
            return false;

        else if(responsePacket.getHeader().seq != 0)
            return false;

        else if(responsePacket.getHeader().ack != 0)
            return false;

        else if(responsePacket.getHeader().flag != requestPacket.getHeader().flag)
            return false;

        else if(responsePacket.getData().length != 1)
            return false;

        else if(responsePacket.getData()[0] != requestPacket.getData()[0])
            return false;

        return true;
    }

    private boolean validateReceivedPacket(Packet responsePacket, int identificator) {

        if(responsePacket.getHeader().identif != identificator)
            return false;

        else if(responsePacket.getHeader().ack != 0)
            return false;

        else if(responsePacket.getHeader().flag != S &&
                responsePacket.getHeader().flag != F &&
                responsePacket.getHeader().flag != R &&
                responsePacket.getHeader().flag != 0)
            return false;

        else if(  (responsePacket.getHeader().flag == F || responsePacket.getHeader().flag == R ) &&
                responsePacket.getData().length != 0)
            return false;

        return true;
    }

    private void validateReceivedAckPacket(Packet responsePacket, int identificator, int ack, boolean fin) throws ServerRstException, LogicException {

        if(responsePacket.getHeader().identif != identificator)
            return;

        else if(responsePacket.getHeader().seq != 0)
            return;

        else if(responsePacket.getHeader().flag != S &&
                responsePacket.getHeader().flag != F &&
                responsePacket.getHeader().flag != R &&
                responsePacket.getHeader().flag != 0)
            return;

        else if(  (responsePacket.getHeader().flag == F || responsePacket.getHeader().flag == R ) &&
                responsePacket.getData().length != 0)
            return;

        if(fin) {
            return;
        }
        else if(responsePacket.getHeader().flag == F) throw  new LogicException("pri uploadu v paketu priznak F", ack,0);
        if(responsePacket.getHeader().flag == S) throw  new LogicException("v datovem paketu nastaven priznak S", ack,0);
        if(responsePacket.getHeader().flag == R) throw new ServerRstException();

    }


    private void tryToFillSpace(int ackNum, List<Packet> extraData, List<Packet> filler) {
        for(int i = 0 ; i < extraData.size() ; ++i) {
            boolean changed = false;

            for(Packet p : extraData) {
                if(p.getHeader().seq == ( (ackNum) & 0xFFFF)) {
                    filler.add(p);
                    ackNum =  ( ackNum + p.getData().length ) & 0xFFFF;
                    extraData.remove(p);
                    changed = true;
                    break;
                }
            }

            if(!changed) break;
        }
    }

    private void printPacket(Packet p, String s){
        System.out.print(s + " ");
        System.out.println(p.toString());

    }

    /** SET UP CONNECTION  **/
    private void setUpConnection(byte[] data) throws IOException, MaxSendException, LogicException, ConnectionException {

        /* send connection request */
        Packet requestPacket = new Packet( new Header(identificator,0,0,S),  data);
        DatagramPacket connectionRequestDatagram = datagramHelper.createDatagramFromPacket(requestPacket);
        socket.send(connectionRequestDatagram);

        /* debug print request */
        printPacket(requestPacket,"SEND");

        /* receive connection response */
        List<DatagramPacket> d = new ArrayList<>();
        d.add(connectionRequestDatagram);
        DatagramPacket response = receiveDatagramPacketListResponse(MAX_PACKET_LENGTH, d);

        /* debug print response */
        Packet responsePacket = datagramHelper.createPacketFromDatagram(response);
        printPacket(responsePacket,"RECV");

        /* validate response */
        if(! validateConnectionPacket(requestPacket,responsePacket))
            throw new  ConnectionException();

        identificator = responsePacket.getHeader().identif;
    }


    /** RECEIVE DATA  **/
    private int receiveAndSavePhoto(String dest) throws IOException, MaxSendException, LogicException, ServerRstException {
        int totalBytes = 0;
        int ackNum = 0;
        List<Packet> extraData = new ArrayList<>();
        FileOutputStream fos = new FileOutputStream(dest);

        while(true) {

            /* receive data response */
            Packet ackPacket = new Packet( new Header(identificator,0, ackNum,0), emptyData );
            DatagramPacket ack = datagramHelper.createDatagramFromPacket(ackPacket);
            List<DatagramPacket> d = new ArrayList<>();
            d.add(ack);
            DatagramPacket response = receiveDatagramPacketListResponse(MAX_PACKET_LENGTH, d);

            /* debug print response */
            Packet responsePacket = datagramHelper.createPacketFromDatagram(response);
            printPacket(responsePacket, "RECV");

            /* validate response */
            if(! validateReceivedPacket(responsePacket, identificator))
                throw new LogicException("packet neprosel validation", 0, ackNum);

            /* check flags */
            if(responsePacket.getHeader().flag == F) break;
            if(responsePacket.getHeader().flag == S) throw  new LogicException("v datovem paketu nastaven priznak S",0, ackNum);
            if(responsePacket.getHeader().flag == R) throw new ServerRstException();

            /* response in order */
            if(responsePacket.getHeader().seq ==  ackNum){

                List<Packet> buffer = new ArrayList<>();
                buffer.add(responsePacket);

                if( ! extraData.isEmpty() )
                    tryToFillSpace(((ackNum + responsePacket.getData().length) & 0xFFFF), extraData, buffer);

                for(Packet p : buffer)
                    ackNum = ((ackNum + p.getData().length) & 0xFFFF);

                /* write to file */
                for(Packet p : buffer) {
                    totalBytes += p.getData().length;
                    fos.write(p.getData());
                }
            }

            /* response not in order */
            else
                extraData.add(responsePacket);

            /* send ack response */
            ackPacket = new Packet( new Header(identificator,0, ackNum,0), emptyData );
            ack = datagramHelper.createDatagramFromPacket(ackPacket);
            socket.send(ack);

            /* debug print response */
            printPacket(ackPacket, "SEND");
        }

        /* close file */
        fos.close();

        /* end connection  */
        socket.send(datagramHelper.createDatagramFromPacket( new Packet( new Header(identificator,0, ackNum,F), emptyData )));
        socket.close();

        return totalBytes;
    }

    class Window {
        List<Packet> packetList;
        List<Packet> window;
        int finalSeq;

        Window(String dataFile, int windowSize) throws IOException {

            /* create window */
            packetList = createPacketsFromByteArray(loadFileToMemory(dataFile));
            window = new ArrayList<>();
            for (Iterator<Packet> iterator = packetList.iterator(); iterator.hasNext(); ) {
                window.add(iterator.next());
                iterator.remove();
                if (window.size() == windowSize)
                    break;
            }

            /* set final packet */
            Packet lastPacket = packetList.get(packetList.size() - 1);
            finalSeq = lastPacket.getHeader().seq + lastPacket.getData().length;

        }

        List<Packet> slide(int startSeq) {
            window = findBySeqNumToEnd(window, startSeq);

            List<Packet> newlyAdded = new ArrayList<>();

            for(Iterator<Packet> iterator = packetList.iterator() ; iterator.hasNext()  && window.size() <= 8 ; ) {
                Packet p = iterator.next();
                window.add(p);
                newlyAdded.add(p);
                iterator.remove();
            }
            return newlyAdded;
        }

        private List<Packet> findBySeqNumToEnd(List<Packet> window, int fromSeq) {
            List<Packet> newOnes = new ArrayList<>();
            for (int i = 0; i < window.size(); ++i)
                if (window.get(i).getHeader().seq == fromSeq)
                    for (int j = i; j < window.size(); ++j)
                        newOnes.add(window.get(j));

            return newOnes;
        }
        //ok
        private List<Packet> createPacketsFromByteArray(byte[] dataFile) {
            List<Packet> packetList = new ArrayList<>();

        /* for each 255 bits */
            for(int i = 0 ; i < dataFile.length - ( dataFile.length % 255 ) ; i+=255) {

            /* create packet */
                byte[] data = new byte[255];
                System.arraycopy(dataFile, i, data, 0, 255);
                Packet p = new Packet(new Header(identificator, (i & 0xFFFF) ,0,0), data);
                packetList.add( p );
            }

        /* last smaller packet may exist */
            if(dataFile.length % 255 != 0){

                int cntRestBits = dataFile.length % 255;
                int firstPos = dataFile.length - cntRestBits;
                byte[] data = new byte[cntRestBits];
                System.arraycopy(dataFile, firstPos, data, 0, cntRestBits);

                Packet p =new Packet(new Header(identificator, (  firstPos & 0xFFFF) ,0,0), data);
                packetList.add( p );
            }

            return packetList;
        }
        //ok
        private byte[] loadFileToMemory(String firmware) throws IOException {

            FileInputStream fis = new FileInputStream(firmware);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            int nRead;
            byte[] data = new byte[16384];

            while ((nRead = fis.read(data, 0, data.length)) != -1)
                buffer.write(data, 0, nRead);

            buffer.flush();

            return buffer.toByteArray();

        }
        //ok
        boolean allConfirmed(int ack) { return packetList.isEmpty() && ack == finalSeq; }
        //ok
        int getFinalSeq() { return finalSeq; }
        //ok
        List<Packet> getWindow() { return window; }
        //ok
        Packet getPacket(int seq) {
            for (Packet aWindow : window)
                if (aWindow.getHeader().seq == seq)
                    return aWindow;
            return null;
        }

        boolean ackInRange(int ack) {

            int firstSeq = window.get(0).getHeader().seq;
            int nextSeq = packetList.isEmpty() ? finalSeq : packetList.get(0).getHeader().seq;

            //pretenecni
            if(firstSeq > nextSeq) {
                nextSeq +=0xFFFF; // narovname konec
                if(ack < firstSeq) ack += 0xFFFF; // narovname ack
            }

            // potencialne to muze jeste byt smeti od predchoziho ramce


            if(firstSeq <= nextSeq && firstSeq <= ack && ack <= nextSeq)
                return true;

            if(2295 <= firstSeq ) {
                firstSeq -= 2295;
                nextSeq -= 2295;
            }
            else {
                firstSeq = 0xFFFF - (2295 - firstSeq);
            }

            //pretenecni
            if(firstSeq > nextSeq) {
                nextSeq +=0xFFFF; // narovname konec
                if(ack < firstSeq) ack += 0xFFFF; // narovname ack
            }


            return firstSeq <= nextSeq && firstSeq <= ack && ack <= nextSeq;
        }




        boolean ackInWindow(int ack) {
            int nextSeq = packetList.isEmpty() ? finalSeq : packetList.get(0).getHeader().seq;

            if (ack == nextSeq)
                return true;

            for(Packet p : window)
                if(p.getHeader().seq == ack)
                    return true;

            return false;
        }
    }

    class Number {
        int cnt;
        int value;

        Number() {
            cnt = 0;
            value = 0;
        }

        void update(int value){

            if(this.value == value)
                cnt++;
            else {
                cnt = 1;
                this.value = value;
            }
        }
    }

    /** SEND DATA **/
    private int loadAndUploadFirmware(String firmware) throws IOException, MaxSendException, LogicException, ServerRstException {
        int totalBytes = 0;

        Number latestAckReceived = new Number();
        Number latestSeqSend = new Number();

        /* create and send first window */
        Window window = new Window(firmware,8);
        for(Packet p : window.getWindow()){
            socket.send(datagramHelper.createDatagramFromPacket(p));
            latestSeqSend.update(p.getHeader().seq);
            printPacket(p, "SEND");
        }

        while(true) {

            /* slide window and send new packages */
            for(Packet p : window.slide( latestAckReceived.value )){
                socket.send(datagramHelper.createDatagramFromPacket(p));
                latestSeqSend.update(p.getHeader().seq);
                printPacket(p, "SEND");
            }

            /* send again not delivered package */
            if(latestAckReceived.cnt >= 3) {
                Packet  p = window.getPacket(latestAckReceived.value);

                if(p == null)
                    throw new LogicException("asking for packet out of window", latestAckReceived.value ,0);

                socket.send(datagramHelper.createDatagramFromPacket(p));
                latestSeqSend.update(p.getHeader().seq);
                printPacket(p, "SEND");
            }

            /* check send cnt */
            if(latestSeqSend.cnt == 20)
                throw new MaxSendException();

            /* receive ack */
            List<DatagramPacket> windowDatagram = new ArrayList<>();
            for (Packet p: window.getWindow())
                windowDatagram.add(datagramHelper.createDatagramFromPacket(p));
            DatagramPacket response =  receiveDatagramPacketListResponse(MAX_PACKET_LENGTH,windowDatagram);

            /* debug print response */
            Packet responsePacket = datagramHelper.createPacketFromDatagram(response);

            /* validate response packet */
            validateReceivedAckPacket(responsePacket, identificator, latestAckReceived.value, false);

            /* validate ack */
            if(window.allConfirmed(responsePacket.getHeader().ack)) {
                printPacket(responsePacket,"RECV");
                break;
            }
            /* update ack */
            if(  window.ackInRange(responsePacket.getHeader().ack) &&  window.ackInWindow(responsePacket.getHeader().ack)) {
                latestAckReceived.update(responsePacket.getHeader().ack);
                printPacket(responsePacket,"RECV");
            } else
                printPacket(responsePacket,"RECV!");
        }

        /* Send finish */
        Packet success = new Packet( new Header(identificator, window.getFinalSeq(),0  ,F), emptyData );
        socket.send(datagramHelper.createDatagramFromPacket(success));
        printPacket(success,"SEND");

        for (int i = 0 ; i < 20 ; ++i ){
        /* Receive response */
            List<DatagramPacket> windowDatagram = new ArrayList<>();
            windowDatagram.add(datagramHelper.createDatagramFromPacket(success));
            DatagramPacket response =  receiveDatagramPacketListResponse(MAX_PACKET_LENGTH,windowDatagram);

            Packet responsePacket = datagramHelper.createPacketFromDatagram(response);

            printPacket(responsePacket,"RECV");

            validateReceivedAckPacket( responsePacket, identificator,latestAckReceived.value, true );

            /* check flags */
            if(responsePacket.getHeader().flag != F) continue;
            if(responsePacket.getHeader().flag == S) throw  new LogicException("v datovem paketu nastaven priznak S", latestAckReceived.value,0);
            if(responsePacket.getHeader().flag == R) throw new ServerRstException();

            socket.close();
            return totalBytes;
        }

        throw new LogicException("spojeni nemohlo byt po 20ti pokusech ukonceno", latestAckReceived.value,0);
    }

    private DatagramPacket receiveDatagramPacketListResponse(int responseDataLen, List<DatagramPacket> requestData) throws IOException, MaxSendException, LogicException {

        /// wait for response
        byte[] incoming = new byte[responseDataLen];
        socket.setSoTimeout(100);

        for(int i = 0 ; i < 20 ; ++i){
            DatagramPacket getack = new DatagramPacket(incoming, incoming.length);
            try {
                socket.receive(getack);
            } catch (SocketTimeoutException e) {
                // resend
                for (DatagramPacket p : requestData)
                    socket.send(p);

                continue;
            }

            if(getack.getLength() < 9) throw new LogicException("packet je kratsi nez 9B", 0,0);
            return getack;
        }

        throw new MaxSendException();
    }


    void downloadPhoto(String destPhotoFile) throws IOException {
        Packet rstPacket = new Packet( new Header(identificator,0, 0,R), emptyData);
        DatagramPacket ackRST = datagramHelper.createDatagramFromPacket(rstPacket);

        try {
            setUpConnection(DOWNLOAD);
            int totalBytes = receiveAndSavePhoto(destPhotoFile);
            System.out.println("[USPECH] Spojeni bylo uspesne uzavreno. Bylo preneseno: " + totalBytes + " Bytu.");
            return;
        }

        catch (LogicException logicException) {
            socket.send(ackRST);
            printPacket(rstPacket, "SEND");
            System.out.println("[ERROR] - RST : " + logicException);
        }

        catch (MaxSendException e) {
            socket.send(ackRST);
            printPacket(rstPacket, "SEND");
            System.out.println("[ERROR] - TIMEOUT: Dosazen max. pocet odeslani packetu");
        }

        catch (ConnectionException e) {
            System.out.println("[ERROR] - CONNECTION: spojeni nemohlo byt navazano: packet neprosel validaci");
        }

        catch (ServerRstException e) {
            System.out.println("[ERROR] - RST: Spojeni ukonceno serverem (poslal RST z neznameho duvodu)");
        }

        socket.close();
    }

    void uploadFirmware(String firmware) throws IOException {



        try {
            setUpConnection(UPLOAD);
            int totalBytes = loadAndUploadFirmware(firmware);
            System.out.println("[USPECH] Spojeni bylo uspesne uzavreno. Bylo preneseno: " + totalBytes + " Bytu.");
        }


        catch (LogicException logicException) {
            Packet rstPacket = new Packet( new Header(identificator,logicException.seq, logicException.ack,R), emptyData);
            DatagramPacket ackRST = datagramHelper.createDatagramFromPacket(rstPacket);

            socket.send(ackRST);
            printPacket(rstPacket, "SEND");
            System.out.println("[ERROR] - RST : " + logicException);
        }

        catch (MaxSendException e) {
            Packet rstPacket = new Packet( new Header(identificator,0, 0,R), emptyData);
            DatagramPacket ackRST = datagramHelper.createDatagramFromPacket(rstPacket);
            socket.send(ackRST);
            printPacket(rstPacket, "SEND");
            System.out.println("[ERROR] - TIMEOUT: Dosazen max. pocet odeslani packetu");
        }

        catch (ConnectionException e) {
            System.out.println("[ERROR] - CONNECTION: spojeni nemohlo byt navazano: packet neprosel validaci");
        }

        catch (ServerRstException e) {
            System.out.println("[ERROR] - RST: Spojeni ukonceno serverem (poslal RST z neznameho duvodu)");
        }
    }
}
