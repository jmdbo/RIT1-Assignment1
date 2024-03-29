/**
 * Redes Integradas de Telecomunicacoes I
 * MIEEC 2014/2015
 *
 * neighbour.java
 *
 * Holds neighbor router internal data
 *
 * Created on 7 de Setembro de 2014, 18:00
 * @author  Luis Bernardo
 */
package router;

import java.net.*;
import java.io.*;
import java.util.*;


/**
 * Holds neighbor router internal data
 */
public final class neighbour {
    /** neigbour's name (address) [A,Z] */       
    public char name;
    /** IP address of the neighbour */
    public String ip;
    /** port number of the neighbour */
    public int port;
    /** distance to the neighbour */
    public int dist;
    /** address of the neighbour, includes IP+port */
    public InetAddress netip;
    
// Distance-vector protocols' specific data
    /** Vector received from neighbour router */    
    public Entry[] vec;
    /** Date when the vector was received */
    public Date vec_date;
    /** Vector TTL */
    public long vec_TTL;    // in seconds
        
    /**
     * Return the name of the neighbour
     * @return the character with the name
     */
    public char Name() { return name; }
    /**
     * Return the IP address of the neighbour
     * @return IP address
     */
    public String Ip() { return ip; }
    /**
     * Return the port number of the neighbour
     * @return port number
     */
    public int Port()  { return port; }
    /**
     * Return the distance to the neighbour
     * @return distance
     */
    public int Dist()  { return dist; }
    /**
     * Return the InetAddress object to send messages to the neighbour
     * @return InetAddress object
     */    
    public InetAddress Netip() { return netip; }
    /** Vector-distance protocol specific function:
     *          Returns a vector, if it exists
     * @return  the vector, or null if it does not exists */
    public Entry[] Vec() { return vec_valid()? vec : null; }

    
    /**
     * Parse a string with a compact name, defining the local name
     * @param name  the string
     * @return  true if name is valid, false otherwise
     */
    private boolean parseName(String name) {
        // Clear name
        if (name.length() != 1)
            return false;
        char c= name.charAt(0);
        if (!Character.isUpperCase (c))
            return false;
        this.name= c;
        return true;
    }
    
    /**
     * Constructor - create an empty instance of neighbour
     */
    public neighbour() {
        clear();
    }
    
    /**
     * Constructor - create a new instance of neighbour from parameters
     * @param name      neighbour's name
     * @param ip        ip address
     * @param port      port number
     * @param distance  distance
     */
    public neighbour(char name, String ip, int port, int distance) {
        clear();
        this.ip= ip;
        if (test_IP()) {
            this.name= name;
            this.port= port;
            this.dist= distance;
        } else
            this.ip= null;
    }
    
    /**
     * Constructor - create a clone of an existing object
     * @param src  object to be cloned
     */
    public neighbour(neighbour src) {
        this.name= src.name;
        this.ip= src.ip;
        this.netip= src.netip;
        this.port= src.port;
        this.dist= src.dist;
    }
        
    /**
     * Update the fields of the neighbour object
     * @param name      neighbour's name
     * @param ip        ip address
     * @param port      port number
     * @param distance  distance
     */
    public void update_neigh(char name, String ip, int port, int distance) {
        this.ip= ip;
        if (test_IP()) {
            this.name= name;
            this.port= port;
            this.dist= distance;
        } else
            clear();
    }
    
    /**
     * Vector-distance specific function:
     *  updates last vector received from neighbor TTL in miliseconds
     * @param vec  vector
     * @param TTL  Time to Live
     * @throws java.lang.Exception Invalid neighbour
     */
    public void update_vec(Entry[] vec, long TTL) throws Exception {
        if (!is_valid())
            throw new Exception ("Update vector of invalid neighbor");
        this.vec= vec;
        this.vec_date= new Date();  // Now
        this.vec_TTL= TTL;
    }
    
    /**
     * Clear the contents of the neigbour object
     */
    public void clear() {
        this.name= ' ';
        this.ip= null;
        this.netip= null;
        this.port= 0;
        this.dist= router.MAX_DISTANCE;
        this.vec= null;
        this.vec_date= null;
        this.vec_TTL= 0;
    }

    /**
     * Test the IP address
     * @return true if is valid, false otherwise
     */
    private boolean test_IP() {
        try {
            netip= InetAddress.getByName(ip);
            return true;
        }
        catch (UnknownHostException e) {
            netip= null;
            return false;
        }
    }

    /**
     * Test if the neighbour is valid
     * @return true if is valid, false otherwise
     */
    public boolean is_valid() { return (netip!=null); }
    
    /**
     * Vector-distance protocol specific: test if the vector is valid
     * @return true if is valid, false otherwise
     */
    public boolean vec_valid() { 
        Date ttl = new Date(vec_TTL*1000);
        if(vec_date==null){
            return false;
        }
        long sum = ttl.getTime() + vec_date.getTime();
        Date now = new Date();
        System.out.println("Now: "+now+"/n TTL: "+vec_TTL+" seconds - Expiration date: "+sum);
        return (vec!=null && (now.getTime()<sum)); 
        // TO DO - it should also test if the time elapsed since vec_date is less than TTL
    }
        
    /**
     * Send a packet to the neighbour
     * @param ds  datagram socket
     * @param dp  datagram packet with the packet contents
     * @throws IOException Error sending packet
     */
    public void send_packet(DatagramSocket ds, 
                                DatagramPacket dp) throws IOException {
        try {
            dp.setAddress(this.netip);
            dp.setPort(this.port);
            ds.send(dp);
        }
        catch (IOException e) {
            throw e;
        }        
    }
    
    /**
     * Send a packet to the neighbour
     * @param ds  datagram socket
     * @param os  output stream with the packet contents
     * @throws IOException Error sending packet
     */
    public void send_packet(DatagramSocket ds, 
                                ByteArrayOutputStream os) throws IOException {
        try {
            byte [] buffer = os.toByteArray();
            DatagramPacket dp= new DatagramPacket(buffer, buffer.length, 
                this.netip, this.port);
            ds.send(dp);
        }
        catch (IOException e) {
            throw e;
        }        
    }
    
    /**
     * Create a send a HELLO packet to the neighbour
     * @param ds    datagram socket
     * @param win   main window object 
     * @return true if sent successfully, false otherwise
     */
    public boolean send_Hello(DatagramSocket ds, router win) {
        // Send HELLO packet
        ByteArrayOutputStream os= new ByteArrayOutputStream();
        DataOutputStream dos= new DataOutputStream(os);
        try {
            dos.writeByte(router.PKT_HELLO);
            // name ('letter')
            dos.writeChar(win.local_name());
            // Distance
            dos.writeInt(dist);
            send_packet(ds, os);
            win.HELLO_snt++;
            return true;
        }
        catch (IOException e) {
            System.out.println("Internal error sending packet HELLO: "+e+"\n");
            return false;
        }        
    }
    
    /**
     * Create a send a BYE packet to the neighbour
     * @param ds    datagram socket
     * @param win   main window object 
     * @return true if sent successfully, false otherwise
     */
    public boolean send_Bye(DatagramSocket ds, router win) {
        ByteArrayOutputStream os= new ByteArrayOutputStream();
        DataOutputStream dos= new DataOutputStream(os);
        try {
            dos.writeByte(router.PKT_BYE);
            dos.writeChar(win.local_name());
            send_packet(ds, os);
            win.BYE_snt++;
            return true;
        }
        catch (IOException e) {
            System.out.println("Internal error sending packet BYE: "+e+"\n");
            return false;
        }        
    }
    
    /**
     * return a string with the neighbour contents; replaces default function
     * @return string with the neighbour contents
     */
    @Override
    public String toString() {
        String str= ""+name;
        if (name == ' ')
            str= "INVALID";
        return "("+name+" ; "+ip+" ; "+port+" ; "+dist+")";
    }
    
    /**
     * parses a string for the neighbour field values
     * @param str  string with the values
     * @return true if parsing successful, false otherwise
     */
    public boolean parseString(String str) {
        StringTokenizer st = new StringTokenizer(str, " ();");
        if (st.countTokens( ) != 4)
            return false;
        try {
            // Parse name
            String _name= st.nextToken();
            if (!parseName(_name))
                return false;
            if (!_name.equals(""+name))
                return false;
            String _ip= st.nextToken();
            int _port= Integer.parseInt(st.nextToken());
            int _dist= Integer.parseInt(st.nextToken());
            update_neigh(name, _ip, _port, _dist);
            return is_valid();
        }
        catch (NumberFormatException e) {
            return false;
        }
    }
}
