/**
 * Redes Integradas de Telecomunicacoes I
 * MIEEC 2014/2015
 *
 * Entry.java
 *
 * Auxiliary class to hold Routing table entries
 *
 * Created on 7 de Setembro de 2014, 18:00
 * @author  Luis Bernardo
 */
package router;


public class RouteEntry extends Entry {

// Fields inherited from Entry
//    public char dest;
//    public int dist;
    
// New fields
    /** next hop */
    public char next_hop;
    
    /** Holdown counter */
    public int holddownCounter;
    
    //Indentifies if the entry is in Hold Down or not
    public boolean isHolddown;
    
    //Distance vector had before going into Hold Down
    public int distHolddown;
    // Declare here a field to store one holdown counter
    //     and add the additional necessary methods to handle it!

    /**
     * Constructor - create an empty instance to a destination
     * @param dest destination address
     */
    public RouteEntry(char dest) {
        super(dest, router.MAX_DISTANCE);
        next_hop= ' ';
        this.isHolddown=false;
        this.holddownCounter=0;
    }

    /**
     * Constructor - clone an existing entry
     * @param src  object that will be cloned
     */
    public RouteEntry(RouteEntry src) {
        super(src);
        this.next_hop= src.next_hop;
        this.holddownCounter = src.holddownCounter;
        this.isHolddown = src.isHolddown;
        this.distHolddown = src.distHolddown;
    }

    /**
     * Constructor - create an entry defining all fields
     * @param dest      destination address
     * @param next_hop  next hop address
     * @param dist      distance to next hop
     */
    public RouteEntry(char dest, char next_hop, int dist) {
        super(dest, dist);
        this.next_hop= next_hop;
        this.isHolddown = false;
        this.holddownCounter = 0;
    }
    
// Holdown algorithm specific field

}
