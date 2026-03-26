package Team;

import java.awt.*;

public class DarkGraySniper extends DarkGrayTeamBase {

    protected boolean isLeaderBot() { return false; }
    protected double preferredDistance() { return 420.0; }
    protected double roleOffsetAngle() { return 0.22; }
    protected double fireCap() { return 2.1; }
    protected double aggression() { return 0.48; }

    protected Color bodyColor() { return new Color(50, 50, 50); }
    protected Color gunColor() { return new Color(20, 20, 20); }
    protected Color radarColor() { return new Color(140, 140, 140); }
    protected Color bulletColor() { return Color.GREEN; }
    protected Color scanColor() { return Color.WHITE; }
}