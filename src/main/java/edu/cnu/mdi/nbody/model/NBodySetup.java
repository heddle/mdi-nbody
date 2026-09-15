package edu.cnu.mdi.nbody.model;

import java.util.*;

/** Editable initial conditions. Unlike {@link NBodyModel}, a setup may be incomplete. */
public final class NBodySetup {
    private final LinkedHashMap<Integer,Body> bodies=new LinkedHashMap<>();
    private Parameters parameters;
    public NBodySetup(Collection<Body> bodies,Parameters parameters) {
        this.parameters=Objects.requireNonNull(parameters);
        for (Body body:bodies) put(body);
    }
    public static NBodySetup empty(Parameters parameters) { return new NBodySetup(List.of(),parameters); }
    public List<Body> bodies() { return List.copyOf(bodies.values()); }
    public Parameters parameters() { return parameters; }
    public void setParameters(Parameters parameters) { this.parameters=Objects.requireNonNull(parameters); }
    public int size() { return bodies.size(); }
    public boolean canRun() { return size()>=2 && size()<=50; }
    public int nextId() { return bodies.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1)+1; }
    public void put(Body body) {
        Objects.requireNonNull(body);
        if (!bodies.containsKey(body.id()) && bodies.size()>=50) throw new IllegalStateException("Use at most 50 bodies");
        bodies.put(body.id(),body);
    }
    public void remove(int id) { bodies.remove(id); }
    public void clear() { bodies.clear(); }
    public void centerOnMass() {
        if (bodies.isEmpty()) return;
        double mass=0,x=0,y=0;
        for (Body b:bodies.values()) { mass+=b.mass(); x+=b.mass()*b.x(); y+=b.mass()*b.y(); }
        transform(1,1,-x/mass,-y/mass);
    }
    public void removeNetMomentum() {
        if (bodies.isEmpty()) return;
        double mass=0,px=0,py=0;
        for (Body b:bodies.values()) { mass+=b.mass(); px+=b.mass()*b.vx(); py+=b.mass()*b.vy(); }
        double dvx=px/mass,dvy=py/mass;
        for (Body b:List.copyOf(bodies.values())) put(new Body(b.id(),b.mass(),b.x(),b.y(),b.vx()-dvx,b.vy()-dvy));
    }
    public void reverseVelocities() {
        for (Body b:List.copyOf(bodies.values())) put(new Body(b.id(),b.mass(),b.x(),b.y(),-b.vx(),-b.vy()));
    }
    public void scalePositions(double factor) {
        if (!(factor>0) || !Double.isFinite(factor)) throw new IllegalArgumentException("Scale must be positive and finite");
        transform(factor,1,0,0);
    }
    public void scaleVelocities(double factor) {
        if (!Double.isFinite(factor)) throw new IllegalArgumentException("Scale must be finite");
        transform(1,factor,0,0);
    }
    public Body duplicate(int id) {
        Body b=Objects.requireNonNull(bodies.get(id),"Unknown body");
        Body copy=new Body(nextId(),b.mass(),b.x()+.1,b.y()+.1,b.vx(),b.vy()); put(copy); return copy;
    }
    public void setCircularVelocity(int satelliteId,int primaryId) {
        if (satelliteId==primaryId) throw new IllegalArgumentException("Select two different bodies");
        Body s=Objects.requireNonNull(bodies.get(satelliteId),"Unknown satellite");
        Body p=Objects.requireNonNull(bodies.get(primaryId),"Unknown primary");
        double dx=s.x()-p.x(),dy=s.y()-p.y(),r=Math.hypot(dx,dy);
        if (!(r>0)) throw new IllegalArgumentException("Bodies must not coincide");
        double speed=Math.sqrt(parameters.g()*p.mass()/r);
        put(new Body(s.id(),s.mass(),s.x(),s.y(),p.vx()-speed*dy/r,p.vy()+speed*dx/r));
    }
    private void transform(double positionScale,double velocityScale,double dx,double dy) {
        for (Body b:List.copyOf(bodies.values())) put(new Body(b.id(),b.mass(),b.x()*positionScale+dx,
                b.y()*positionScale+dy,b.vx()*velocityScale,b.vy()*velocityScale));
    }
}
