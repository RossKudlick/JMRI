package jmri.profile;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import javax.swing.table.AbstractTableModel;

/**
 *
 * @author rhwood
 */
public class ProfileTableModel extends AbstractTableModel implements PropertyChangeListener {

    private static final long serialVersionUID = -4425501077376055668L;

    @SuppressWarnings("LeakingThisInConstructor")
    public ProfileTableModel() {
        ProfileManager.defaultManager().addPropertyChangeListener(this);
    }

    @Override
    public int getRowCount() {
        return ProfileManager.defaultManager().getAllProfiles().size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Profile p = ProfileManager.defaultManager().getAllProfiles().get(rowIndex);
        switch (columnIndex) {
            case -1: // tooltip
                return Bundle.getMessage("ProfileTableModel.toolTip", p.getName(), p.getPath(), p.getId(), this.getValueAt(rowIndex, 2));
            case 0:
                return p.getName();
            case 1:
                return p.getPath();
            case 2:
                if (p.equals(ProfileManager.defaultManager().getActiveProfile())) {
                    return Bundle.getMessage("ProfileTableModel.isActive"); // NOI18N
                } else if (p.equals(ProfileManager.defaultManager().getNextActiveProfile())) {
                    return Bundle.getMessage("ProfileTableModel.nextActive"); // NOI18N
                } else {
                    return ""; // NOI18N
                }
            default:
                return null;
        }
    }

    @Override
    public String getColumnName(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return Bundle.getMessage("ProfileTableModel.profileName"); // NOI18N
            case 1:
                return Bundle.getMessage("ProfileTableModel.profilePath"); // NOI18N
            case 2:
                return Bundle.getMessage("ProfileTableModel.profileState"); // NOI18N
            default:
                return null;
        }
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return String.class;
            case 1:
                return File.class;
            case 2:
                return String.class;
            default:
                return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                ProfileManager.defaultManager().getAllProfiles().get(rowIndex).setName(aValue.toString());
                break;
            default:
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        this.fireTableDataChanged();
    }
}
