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
    // Declare here a field to store one holdown counter
    //     and add the additional necessary methods to handle it!

    /**
     * Constructor - create an empty instance to a destination
     * @param dest destination address
     */
    public RouteEntry(char dest) {
        super(dest, router.MAX_DISTANCE);
        next_hop= ' ';
    }

    /**
     * Constructor - clone an existing entry
     * @param src  object that will be cloned
     */
    public RouteEntry(RouteEntry src) {
        super(src);
        next_hop= src.next_hop;
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
    }
    
// Holdown algorithm specific field

}
