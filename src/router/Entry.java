/**
 * Redes Integradas de Telecomunicacoes I
 * MIEEC 2014/2015
 *
 * Entry.java
 *
 * Auxiliary class to hold ROUTE vector information
 *
 * Created on 7 de Setembro de 2014, 18:00
 * @author  Luis Bernardo
 */
package router;

import java.io.*;

/** 
 * Hold ROUTE vector elements 
 */
public class Entry {
    
    /** Destination */
    public char dest;
    /** Distance */
    public int dist;
    
    /** Create a new instance of Entry */
    public Entry(){
        this.dest= ' ';
        this.dist= -1;
    }
    
    /**
     * Create a new instance of Entry
     * @param dest  destination address
     * @param dist  distance
     */
    public Entry(char dest, int dist){
        this.dest= dest;
        this.dist= dist;
    }
    
    /**
     * Create a new instance of Entry dupping another object
     * @param src object that will be copied
     */
    public Entry(Entry src){
        this.dest= src.dest;
        this.dist= src.dist;
    }
    
    /**
     * Create a new instance of Entry from an input stream
     * @param dis  input stream
     * @throws java.io.IOException  Read error
     */
    public Entry(DataInputStream dis) throws java.io.IOException {
        readEntry(dis);
    }
 
    /**
     * Update the Entry fields
     * @param dest  new destination
     * @param dist  new distance
     */
    public void update(char dest, int dist) {
        this.dest= dest;
        this.dist= dist;
    }

    /**
     * Update the distance field
     * @param dist  new distance
     */
    public void update_dist(int dist) {
        this.dist= dist;
    }
    
    /**
     * Return a string with the entry contents
     * @return string with the entry contents
     */
    @Override
    public String toString() {
        return "("+dest+" , "+dist+")";
    }
    
    /**
     * Compare with another Entry object
     * @param e  an Entry object
     * @return true if entry is equal to e
     */
    public boolean equals_to(Entry e) {
        return (e!=null) && (dest==e.dest) && (e.dist == dist);
    }
    
    /**
     * Compares to the destination field of another object
     * @param e  an Entry object
     * @return true if the destination is equal
     */
    public boolean equals_dest(Entry e) {
        return dest == e.dest;
    }
    
    /**
     * Write the Entry content to a DataOutputStream
     * @param dos  output stream
     * @throws java.io.IOException Write error
     */
    public void writeEntry(DataOutputStream dos) throws java.io.IOException {
        dos.writeChar(dest);
        dos.writeInt(dist);
    }
    
    /**
     * Read the Entry contents from one DataInputStream
     * @param dis  input stream
     * @throws java.io.IOException Read error
     */
    public final void readEntry(DataInputStream dis) throws java.io.IOException {
        dest= dis.readChar();
        if (!Character.isUpperCase(dest)) {
            throw new IOException("Invalid address '"+dest+"'");
        }            
        dist= dis.readInt();
        if ((dist<0) || (dist>router.MAX_DISTANCE)) {
            throw new IOException("Invalid distance '"+dist+"'");
        }
    }
    
}
