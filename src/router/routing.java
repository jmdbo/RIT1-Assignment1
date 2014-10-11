/**
 * Redes Integradas de Telecomunicacoes I MIEEC 2014/2015
 *
 * routing.java
 *
 * Encapsulates the routing functions, hosting multiple instances of
 * Routing_process objects, and handles DATA packets
 *
 * Created on 7 de Setembro de 2014, 18:00
 *
 * @author Luis Bernardo
 */
package router;

import java.util.*;
import java.net.*;
import java.io.*;
import javax.swing.*;
import java.awt.event.*;

/**
 * Encapsulates the routing functions, hosting multiple instances of
 * Routing_process objects, and handles DATA packets
 */
public class routing {
    
    //private Date lastROUTETime;
    /**
     * Maximum length of the Entry vector length
     */
    public final int MAX_ENTRY_VEC_LEN = 10;
    /**
     * Time added to the period to define the TTL field of the ROUTE packets
     */
    public final int TTL_ADD = 10;

    // Variables
    /**
     * Routing table object
     */
    public HashMap<Character, RouteEntry> tab;
    /**
     * Lock to synchronize update of the routing table
     */
    final private Integer tab_lock = new Integer(0);

    /**
     * Local address name
     */
    private char local_name;
    /**
     * Neighbour list
     */
    private neighbourList neig;
    /**
     * Reference to main window with GUI
     */
    private router win;
    /**
     * Unicast datagram socket used to send packets
     */
    private DatagramSocket ds;
    /**
     * Reference to graphical routing table object
     */
    private JTable tableObj;

    public Date lastSending;
    private javax.swing.Timer timer_announce;
    private javax.swing.Timer timer_holddown;

    // Configuration variables
    /**
     * ROUTE sending period
     */
    private final int period;
    /**
     * Min interval between ROUTE
     */
    private final int min_interval;
    /**
     * Uses Split Horizon with Poison Reverse
     */
    private final boolean splitHorizon;   // If it uses Split Horizon
    /**
     * Uses Hold down
     */
    private final boolean holddown;        // If it uses Holddown
    /**
     * Hold down time [s]
     */
    private final int MAX_holddown;          // Number of periods in holddown

    /**
     * Create a new instance of a routing object, that encapsulates routing
     * processes
     *
     * @param local_name local address
     * @param neig neighbour list
     * @param period ROUTE timer period
     * @param min_interval minimum interval between ROUTE packets sent
     * @param splitHorz use Split Horizon
     * @param holddwn use Hold down
     * @param MAX_holddwn Hold down time
     * @param win reference to main window object
     * @param ds unicast datagram socket
     * @param TabObject Graphical object with the
     */
    public routing(char local_name, neighbourList neig, int period,
            int min_interval, boolean splitHorz, boolean holddwn, int MAX_holddwn,
            router win, DatagramSocket ds, JTable TabObject) {
        this.local_name = local_name;
        this.neig = neig;
        this.period = period;
        this.min_interval = min_interval * 1000;
        this.splitHorizon = splitHorz;
        this.holddown = holddwn;
        this.MAX_holddown = MAX_holddwn;
        this.win = win;
        this.ds = ds;
        this.tableObj = TabObject;
        // Initialize everything
        this.timer_announce = null;
        this.tab = new HashMap<>();
        Log2("new routing(local='" + local_name + "', period=" + period
                + ", min_interval=" + min_interval + (splitHorizon ? ", splitHorizon" : "")
                + (holddown ? (", holddown(" + MAX_holddown + ")") : "") + ")");
    }

    /**
     * Starts routing thread
     *
     * @return true if successful
     */
    public boolean start() {
        start_announce_timer();
        update_routing_table();        
        start_holddownTimer();
        return true;
    }

    /**
     * Handle a network change notification
     *
     * @param send_always if true, send always the ROUTE packet
     */
    public void network_changed(boolean send_always) {
        if (win.is_sendIfChanges()) {
            long timeRoute;
            //Checks if lastROUTETime is null
            if(lastSending==null){
                //If it is, defines time as 0;
                timeRoute = 0;
            } else{
                //If not, we convert the date into a long integer
                timeRoute = lastSending.getTime();
            }
            //We save the current date into a variable
            Date nowDate = new Date();
            long now = nowDate.getTime();
            //If min_interval has elapsed, we send the route package immediatly
            //if(now>= timeRoute + (min_interval)){
            if(test_time_since_last_update()){
                timer_announce.stop();
                //lastROUTETime = new Date();
                timer_announce.setInitialDelay(0);
                timer_announce.start();
            } 
            //If not we reschedule the timer to send the route package as soon
            //as the minimum delay expires.
            else {
                reschedule_announce_timer();
            }
            // Recalculate the table and send it if send_always or if the table changed
            // Control the time between ROUTEs; 
            //     if min_interval was not reached sleep until that time before sending the ROUTE packet.
        }
    }

    /**
     * Stop all the routing processes and resets the routing state
     */
    public void stop() {
        stop_announce_timer();
        // Clean routing table
        tab.clear();

        update_routing_window();

        local_name = ' ';
        neig = null;
        win = null;
        ds = null;
        tableObj = null;
    }

    /**
     * Sends a ROUTE packet with route vector to neighbour n
     *
     * @param n neighbour reference
     * @return true if successful, false otherwise
     */
    public boolean send_local_ROUTE(neighbour n) {
        Log2("send_local_ROUTE(" + n.Name() + ")\n");

        // Prepare and send message
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        try {
            dos.writeByte(router.PKT_ROUTE);
            dos.writeChar(local_name);
            dos.writeInt(period + TTL_ADD);   // TTL value
            dos.writeInt(tab.size());
            for (RouteEntry rt : tab.values()) {
                // This is a good place to put the split horizon implementation ...
                if ((rt.next_hop == n.name) && splitHorizon) {
                    RouteEntry rt_sh = new RouteEntry(rt);
                    if (rt_sh.dist < router.MAX_DISTANCE) {
                        rt_sh.dist = router.MAX_DISTANCE;
                    }
                    rt_sh.writeEntry(dos);
                } else {
                    rt.writeEntry(dos);
                }
            }
            byte[] buffer = os.toByteArray();
            DatagramPacket dp = new DatagramPacket(buffer, buffer.length);

            n.send_packet(ds, dp);
            lastSending = new Date();
            win.ROUTE_snt++;
            return true;
        } catch (IOException e) {
            Log("Error sending ROUTE: " + e + "\n");
            return false;
        }
    }

    /**
     * Send local ROUTE for all neighbours
     *
     * @return true if successful, false otherwise
     */
    public boolean send_local_ROUTE() {
        
        if ((tab == null) || tab.isEmpty()) {
            Log2("Cannot send ROUTE: invalid routing table\n");
            return true;
        }
        // send the local vector to all the neighbor routers, one by one
        //    using the method above (send_local_ROUTE(neighbour))
        for (neighbour pt : neig.values()) {
            send_local_ROUTE(pt);
            Log("Sending ROUTE to " + pt + "\n");
        }
        return true;
    }

    /**
     * Compare the routing tables rt1 and rt2
     *
     * @param rt1 - routing table 1
     * @param rt2 - routing table 2
     * @return true if they are equal, false otherwise
     */
    public static boolean routing_tables_equal(HashMap<Character, RouteEntry> rt1, HashMap<Character, RouteEntry> rt2) {
        if ((rt1 == null) || (rt2 == null)) {
            return false;
        }
        if (rt1.size() != rt2.size()) {
            return false;
        }
        for (RouteEntry re : rt1.values()) {
            if (!re.equals_to(rt2.get(re.dest))) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Compare the entry vectors vec1 and vec2
     *
     * @param vec1 - Entry vector 1
     * @param vec2 - Entry vector 2
     * @return true if they are equal, false otherwise
     */
    public static boolean entry_vectors_equal(Entry[] vec1, Entry[] vec2){
        if((vec1==null) || (vec2 == null))
            return false;
        if (vec1.length != vec2.length)
            return false;
        
        for (int i = 0; i < vec2.length; i++) {
             if(vec1[i].dest != vec2[i].dest || vec1[i].dist != vec2[i].dist){
                 return false;
             }            
        }
        return true;
    }

    /**
     * Get the routing table contents
     *
     * @return the routing table
     */
    public HashMap<Character, RouteEntry> get_routing_table() {
        return tab;
    }

    /**
     * Unmarshall a ROUTE packet and process it
     *
     * @param sender the sender address
     * @param dp datagram packet
     * @param ip IP address of the sender
     * @param dis input stream object
     * @return true if packet was handled successfully, false if error
     */
    public boolean process_ROUTE(char sender, DatagramPacket dp,
            String ip, DataInputStream dis) {
        //Log("Packet ROUTE not supported yet\n");
        if (sender == local_name) {
            // Packet loopback - ignore
            return true;
        }
        Entry[] data;
        try {
            Log("PKT_ROUTE");
            String aux;
            aux = "(" + sender + ",";
            int TTL = dis.readInt();
            aux += "TTL=" + TTL + ",";
            int n = dis.readInt();
            aux += "List(" + n + ": ";
            if ((n <= 0) || (n > 30)) {
                Log("\nInvalid list length '" + n + "'\n");
                return false;
            }
            data = new Entry[n];
            for (int i = 0; i < n; i++) {
                try {
                    data[i] = new Entry(dis);
                } catch (IOException e) {
                    Log("\nERROR - Invalid vector Entry: " + e.getMessage() + "\n");
                    return false;
                }
                aux += (i == 0 ? "" : " ; ") + data[i].toString();
            }
            Log(aux + ")\n");

            neighbour pt = neig.locate_neig(dp.getAddress().getHostAddress(), dp.getPort());
            if (pt == null) {
                Log("\nERROR - Invalid sender (" + dp.getAddress().getHostAddress() + " ; " + dp.getPort() + "), it is not a neighbor\n");
                return false;
            }
            if (pt.Name() != sender) {
                Log("\nERROR - Invalid sender name (" + sender + "), different from the neigbour table\n");
                return false;
            }
            // Update router vector
            //Code to store the vector received in the neighbour object associated
            //If the neighbour has no Entries vector, updates it and calls
            //Network changed
            if (pt.Vec() == null) {
                pt.update_vec(data, TTL);
                network_changed(false);

            } 
            //If the vector stored is different from the one received updates it
            //And calls network_changed
            else if (!entry_vectors_equal(pt.Vec(), data)) {
                pt.update_vec(data, TTL);
                network_changed(false);
            }
            update_routing_window();           
            return true;
        } catch (IOException e) {
            Log("\nERROR - Packet too short\n");
            return false;
        } catch (Exception e) {
            Log("\nERROR - Invalid neighbour update\n");
            return false;
        }
    }

    /**
     * Test if a path is available to a destination through a neighbour
     *
     * @param n neighbour reference
     * @param dest destination address
     * @return true if available, false otherwise
     */
    private boolean is_path_available(neighbour n, char dest) {
        if (!n.vec_valid()) {
            return false;
        }
        for (Entry Vec : n.Vec()) {
            if (Vec.dest == dest) {
                return Vec.dist < router.MAX_DISTANCE;
            }
        }
        return false;
    }

    /**
     * Calculate the routing table
     *
     * @return true if the routing table was modified, false otherwise
     */
    private synchronized boolean update_routing_table() {
        HashMap<Character, RouteEntry> baktab = tab;
        synchronized (tab_lock) {
            tab = new HashMap<>();

            // Add local node
            tab.put(local_name, new RouteEntry(local_name, ' ', 0));
            
            /*Verifies if the route to a destination through the former optimal
            neighbour still exists, if not puts the route in Hold Down
            */
            for(RouteEntry rt1 : baktab.values()){
                if(neig.locate_neig(rt1.next_hop)==null && holddown && rt1.next_hop!= ' '){                    
                    RouteEntry r_entry = new RouteEntry(rt1);
                    if(!r_entry.isHolddown){
                        r_entry.isHolddown=true;
                        r_entry.holddownCounter=0;
                        r_entry.distHolddown = rt1.dist;
                        r_entry.dist = router.MAX_DISTANCE;
                    }
                    //Checks if the MAX_holddown has been attained
                    if(r_entry.holddownCounter<= MAX_holddown)
                        tab.put(r_entry.dest, r_entry);
                }
            }
            // DV algorithm implementation
            for (neighbour vis : neig.values()) {
                if (vis.Vec() != null) {                    
                    for (Entry ent : vis.vec) {
                        //Checks if the current entry was in holddown and adds them to
                        //the new table if it was in holddown
                        if(baktab.containsKey(ent.dest) && holddown){
                            RouteEntry oldEntry = baktab.get(ent.dest);
                            if(oldEntry.isHolddown && !tab.containsKey(ent.dest)
                                    && oldEntry.holddownCounter<= MAX_holddown){
                                tab.put(oldEntry.dest, new RouteEntry(oldEntry));
                            }
                        }
                        /*If the new table already has an entry checks if the 
                        new entry is a better route than the existent.
                        If not, adds it to the table.
                        */
                        if (tab.containsKey(ent.dest)) {
                            RouteEntry route_old = tab.get(ent.dest);
                            if(route_old.isHolddown){
                                //Verifies if a better route exists while on 
                                //Hold Down
                                if (ent.dist + vis.dist <= route_old.distHolddown) {
                                    RouteEntry r_entry = new RouteEntry(ent.dest, vis.name, ent.dist + vis.dist);
                                    tab.replace(ent.dest, r_entry);
                                }
                            } else{
                                if (ent.dist + vis.dist < route_old.dist) {
                                    RouteEntry r_entry = new RouteEntry(ent.dest, vis.name, ent.dist + vis.dist);
                                    tab.replace(ent.dest, r_entry);
                                }
                            }
                        } else if (ent.dist+vis.dist < router.MAX_DISTANCE ) {
                            RouteEntry r_entry;
                            r_entry = new RouteEntry(ent.dest, vis.name, ent.dist + vis.dist);
                            tab.put(ent.dest, r_entry);
                        } 
                        /*If an infinite distance is received, checks if that 
                        distance has come from the former optimal neighbour and
                        if yes puts the route in hold down.
                        */
                        else if (ent.dist+vis.dist >= router.MAX_DISTANCE && holddown){
                            for(RouteEntry rt1: baktab.values()){
                                if((ent.dest == rt1.dest) && (vis.name == rt1.next_hop) && !rt1.isHolddown){
                                    RouteEntry r_entry;
                                    r_entry = new RouteEntry(ent.dest, vis.name, router.MAX_DISTANCE);
                                    r_entry.distHolddown=rt1.dist;
                                    r_entry.isHolddown = true;
                                    r_entry.holddownCounter = 0;
                                    tab.put(ent.dest, r_entry);                                    
                                }
                            }                            
                        }                        
                    }
                }
            }
        }
        // Echo routing table
        update_routing_window();
        return !routing_tables_equal(tab, baktab);
    }

    /**
     * Display the routing table in the GUI
     */
    public void update_routing_window() {
        Log2("update_routing_window\n");
        // update window
        Iterator<RouteEntry> rit = tab.values().iterator();
        RouteEntry r;
        for (int i = 0; i < tableObj.getRowCount(); i++) {
            if (rit.hasNext()) {
                r = rit.next();
                Log2("(" + r.dest + " : " + r.next_hop + " : " + r.dist + ")");
                tableObj.setValueAt("" + r.dest, i, 0);
                tableObj.setValueAt("" + r.next_hop, i, 1);
                tableObj.setValueAt("" + r.dist, i, 2);
                tableObj.setValueAt(""+ r.isHolddown, i, 3);      
            } else {
                tableObj.setValueAt("", i, 0);
                tableObj.setValueAt("", i, 1);
                tableObj.setValueAt("", i, 2);
                tableObj.setValueAt("", i, 3);
            }
        }
    }


    /* ------------------------------------ */
    // Announce timer
    /**
     * Run the timer responsible for sending periodic ROUTE packets to routers
     * within the area
     *
     * @param duration duration of the first interval in (ms); then on is period
     */
    private void run_announce_timer(int duration) {
        //Log("routing.run_announce_timer not implemented yet\n");
        // Place here the code to create the timer_announce object and define the
        //    timeout event handler
        // It should wait duration ms until triggering the first time;
        //   then on, it should run periodically
        timer_announce = new javax.swing.Timer(duration, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent evt) {                
                update_routing_table();
                send_local_ROUTE();
                //lastROUTETime = new Date();
            }
        });
        timer_announce.setRepeats(true);
        timer_announce.start();

    }

    /**
     * Launches timer responsible for sending periodic distance packets to
     * neighbours
     */
    private void start_announce_timer() {
        run_announce_timer(period * 1000);
    }

    /**
     * Stops the timer responsible for sending periodic distance packets to
     * neighbours
     */
    private void stop_announce_timer() {
        if (timer_announce != null) {
            timer_announce.stop();
            timer_announce = null;
        }
    }

    /**
     * Restarts the timer responsible for sending periodic distance packets to
     * neighbours
     */
    private void reset_announce_timer() {
        if ((timer_announce != null) && timer_announce.isRunning()) {
            stop_announce_timer();
        }
        start_announce_timer();
    }

    /**
     * Tests if the minimum interval time has elapsed since last sending
     *
     * @return true if the time elapsed, false otherwise
     */
    public boolean test_time_since_last_update() {
        return (lastSending == null)
                || ((System.currentTimeMillis() - lastSending.getTime()) >= min_interval);
    }

    /**
     * Reschedules the timer to trigger exactly after a min_interval time since
     * last sending
     */
    public void reschedule_announce_timer() {
        //Log("routing.reschedule_announce_timer not implemented yet\n");
        // use run_announce_timer to wait until min_interval ms before triggering the timer
        //    lastSending stores the time of the last sending of ROUTE
        Date nowDate = new Date();
        long now = nowDate.getTime();
        long timeRoute;
        if(lastSending==null){
            timeRoute=0;
        }else{
            timeRoute=lastSending.getTime();
        }               
        timer_announce.stop();
        int waitTime=(int)((timeRoute+min_interval)-now);
        timer_announce.setInitialDelay(waitTime);
        timer_announce.start();
    }

    /* ------------------------------------ */
    // Holddown timer
    private void start_holddownTimer(){
        timer_holddown = new javax.swing.Timer(1000,new ActionListener(){
            @Override
            public void actionPerformed(ActionEvent evt) { 
                //System.out.println("Timmer Holddown Triggered");
                //update_routing_window();
                for(RouteEntry rt : tab.values()){
                    if(rt.isHolddown){
                        rt.holddownCounter++;
                    }
                }
            }           
        });
        timer_holddown.setRepeats(true);
        timer_holddown.start();
    }
    
    // Complete the code here ...
    /**
     * *************************************************************************
     * DATA HANDLING
     */
    /**
     * returns next hop to reach destination
     *
     * @param dest destination address
     * @return the address of the next hop, or ' ' if not found.
     */
    public char next_Hop(char dest) {
        //Log("routing.next_Hop not implemented yet\n");
        // Place here the code to get the next-hop to reach dest

        if (tab.containsKey(dest)) {
            RouteEntry route = tab.get(dest);
            return route.next_hop;

        } else {
            return ' ';
        }

    }

    /**
     * send a DATA packet using the routing table and the neighbor information
     *
     * @param dest destination address
     * @param dp datagram packet object
     */
    public void send_data_packet(char dest, DatagramPacket dp) {
        if (win.is_local_name(dest)) {
            // Send to local node
            try {
                dp.setAddress(InetAddress.getLocalHost());
                dp.setPort(ds.getLocalPort());
                ds.send(dp);
                win.DATA_snt++;
            } catch (UnknownHostException e) {
                Log("Error sending packet to himself: " + e + "\n");
            } catch (IOException e) {
                Log("Error sending packet to himself: " + e + "\n");
            }

        } else { // Send to neighbour router
            char prox = next_Hop(dest);
            if (prox == ' ') {
                Log("No route to destination: packet discarded\n");
            } else {
                // Lookup neighbour
                neighbour pt = neig.locate_neig(prox);
                if (pt == null) {
                    Log("Invalid neighbour (" + prox
                            + ") in routing table: packet discarder\n");
                    return;
                }
                try {
                    pt.send_packet(ds, dp);
                    win.DATA_snt++;
                } catch (IOException e) {
                    Log("Error sending DATA packet: " + e + "\n");
                }
            }
        }
    }

    /**
     * prepares a data packet; adds local_name to path
     *
     * @param sender sender name
     * @param dest destination name
     * @param seq sequence number
     * @param msg message contents
     * @param path path already transverse
     * @return datagram packet to send
     */
    public DatagramPacket make_data_packet(char sender, char dest,
            int seq, String msg, String path) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(os);
        try {
            dos.writeByte(router.PKT_DATA);
            dos.writeChar(sender);
            dos.writeChar(dest);
            dos.writeInt(seq);
            dos.writeShort(msg.length());
            dos.writeBytes(msg);
            dos.writeByte(path.length() + 1);
            dos.writeBytes(path + win.local_name());
        } catch (IOException e) {
            Log("Error encoding data packet: " + e + "\n");
            return null;
        }
        byte[] buffer = os.toByteArray();
        return new DatagramPacket(buffer, buffer.length);
    }

    /**
     * prepares a data packet; adds local_name to path and send the packet
     *
     * @param sender sender name
     * @param dest destination name
     * @param seq sequence number
     * @param msg message contents
     * @param path path already transverse
     */
    public void send_data_packet(char sender, char dest, int seq, String msg,
            String path) {
        if (!Character.isUpperCase(sender)) {
            Log("Invalid sender '" + sender + "'\n");
            return;
        }
        if (!Character.isUpperCase(dest)) {
            Log("Invalid destination '" + dest + "'\n");
            return;
        }
        DatagramPacket dp = make_data_packet(sender, dest, seq, msg, path);
        if (dp != null) {
            send_data_packet(dest, dp);
        }
    }

    /**
     * unmarshals DATA packet e process it
     *
     * @param sender the sender of the packet
     * @param dp datagram packet received
     * @param ip IP of the sender
     * @param dis data input stream
     * @return true if decoding was successful
     */
    public boolean process_DATA(char sender, DatagramPacket dp,
            String ip, DataInputStream dis) {
        try {
            Log("PKT_DATA");
            if (!Character.isUpperCase(sender)) {
                Log("Invalid sender '" + sender + "'\n");
                return false;
            }
            // Read Dest
            char dest = dis.readChar();
            // Read seq
            int seq = dis.readInt();
            // Read message
            int len_msg = dis.readShort();
            if (len_msg > 255) {
                Log(": message too long (" + len_msg + ">255)\n");
                return false;
            }
            byte[] sbuf1 = new byte[len_msg];
            int n = dis.read(sbuf1, 0, len_msg);
            if (n != len_msg) {
                Log(": Invalid message length\n");
                return false;
            }
            String msg = new String(sbuf1, 0, n);
            // Read path
            int len_path = dis.readByte();
            if (len_path > router.MAX_PATH_LEN) {
                Log(": path length too long (" + len_path + ">" + router.MAX_PATH_LEN
                        + ")\n");
                return false;
            }
            byte[] sbuf2 = new byte[len_path];
            n = dis.read(sbuf2, 0, len_path);
            if (n != len_path) {
                Log(": Invalid path length\n");
                return false;
            }
            String path = new String(sbuf2, 0, n);
            Log(" (" + sender + "-" + dest + "," + seq + "):'" + msg + "':Path='" 
                    + path + win.local_name() + "'\n");
            // Test routing table
            if (win.is_local_name(dest)) {
                // Arrived at destination
                Log("DATA packet reached destination\n");
                return true;
            } else {
                char prox = next_Hop(dest);
                if (prox == ' ') {
                    Log("No route to destination: packet discarded\n");
                    return false;
                } else {
                    // Send packet to next hop
                    send_data_packet(sender, dest, seq, msg, path);
                    return true;
                }
            }
        } catch (IOException e) {
            Log(" Error decoding data packet: " + e + "\n");
        }
        return false;
    }

    /**
     * *************************************************************************
     * Log functions
     */
    /**
     * Output the string to the log text window and command line
     *
     * @param s log string
     */
    private void Log(String s) {
        win.Log(s);
    }

    /**
     * Auxiliary log function - when more detail is required remove the comments
     *
     * @param s log string
     */
    public final void Log2(String s) {
        System.err.println(s);
        //if (win != null)
        //    win.Log(s);  // For detailed debug purposes
    }

    /**
     * Log the content of a routing table object
     *
     * @param tab routing table
     */
    public final void Log_routing_table(HashMap<Character, RouteEntry> tab) {
        if (tab != null) {
            for (RouteEntry rt : tab.values()) {
                Log(rt.toString() + "\n");
            }
        }
    }
}
