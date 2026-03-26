package Team;

import java.io.Serializable;

class EnemyInfo {
    String name;
    double x;
    double y;
    double energy;
    double heading;
    double velocity;
    double turnRate;
    long lastSeen;
    boolean alive = true;
}

class MateInfo {
    String name;
    double x;
    double y;
    double energy;
    double heading;
    long lastSeen;
    boolean alive = true;
}

class EnemyScanMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    double x;
    double y;
    double energy;
    double heading;
    double velocity;
    double turnRate;
    long time;

    EnemyScanMessage(String name, double x, double y, double energy, double heading,
                     double velocity, double turnRate, long time) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.energy = energy;
        this.heading = heading;
        this.velocity = velocity;
        this.turnRate = turnRate;
        this.time = time;
    }
}

class MateStateMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    String name;
    double x;
    double y;
    double energy;
    double heading;
    long time;

    MateStateMessage(String name, double x, double y, double energy, double heading, long time) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.energy = energy;
        this.heading = heading;
        this.time = time;
    }
}

class FocusMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    String targetName;
    double x;
    double y;
    double energy;
    long time;

    FocusMessage(String targetName, double x, double y, double energy, long time) {
        this.targetName = targetName;
        this.x = x;
        this.y = y;
        this.energy = energy;
        this.time = time;
    }
}