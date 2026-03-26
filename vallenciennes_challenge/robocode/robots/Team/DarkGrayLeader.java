package Team;

import java.awt.*;

public class DarkGrayLeader extends DarkGrayTeamBase {

    protected boolean isLeaderBot() { return true; }
    protected double preferredDistance() { return 260.0; }
    protected double roleOffsetAngle() { return 0.0; }
    protected double fireCap() { return 2.6; }
    protected double aggression() { return 0.72; }

    protected Color bodyColor() { return Color.DARK_GRAY; }
    protected Color gunColor() { return Color.BLACK; }
    protected Color radarColor() { return Color.LIGHT_GRAY; }
    protected Color bulletColor() { return Color.CYAN; }
    protected Color scanColor() { return Color.WHITE; }
}