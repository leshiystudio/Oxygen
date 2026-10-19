package sct;

import java.awt.BorderLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

//Таблица параметров «имя — значение» с поиском по имени. Значения редактируются текстом;
//проверка — при применении (Presets.set / PlantConfig.set сообщат об ошибке).
public class ParamTable extends JPanel {
	private static final long serialVersionUID = 1L;
	private final DefaultTableModel model = new DefaultTableModel(new Object[]{"parameter", "value"}, 0) {
		private static final long serialVersionUID = 1L;
		public boolean isCellEditable(int row, int col) {
			return col == 1;
		}
	};
	private final JTable table = new JTable(model) {//true/false — галочкой
		private static final long serialVersionUID = 1L;
		public javax.swing.table.TableCellRenderer getCellRenderer(int row, int col) {
			if (col == 1 && getValueAt(row, col) instanceof Boolean) {
				return getDefaultRenderer(Boolean.class);
			}
			return super.getCellRenderer(row, col);
		}
		public javax.swing.table.TableCellEditor getCellEditor(int row, int col) {
			if (col == 1 && getValueAt(row, col) instanceof Boolean) {
				return getDefaultEditor(Boolean.class);
			}
			return super.getCellEditor(row, col);
		}
	};
	private final JTextField filter = new JTextField();

	public ParamTable() {
		super(new BorderLayout(4, 4));
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<DefaultTableModel>(model);
		table.setRowSorter(sorter);
		table.getColumnModel().getColumn(0).setPreferredWidth(260);
		filter.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { update(); }
			public void removeUpdate(DocumentEvent e) { update(); }
			public void changedUpdate(DocumentEvent e) { update(); }
			private void update() {
				String t = filter.getText().trim();
				sorter.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(t), 0));
			}
		});
		JPanel top = new JPanel(new BorderLayout(4, 4));
		top.add(new JLabel("search:"), BorderLayout.WEST);
		top.add(filter, BorderLayout.CENTER);
		add(top, BorderLayout.NORTH);
		add(new JScrollPane(table), BorderLayout.CENTER);
	}
	public void set_rows(List<String[]> rows) {
		stop_editing();
		model.setRowCount(0);
		for (String[] r : rows) {
			Object v = r[1].equals("true") ? Boolean.TRUE : r[1].equals("false") ? Boolean.FALSE : r[1];
			model.addRow(new Object[]{r[0], v});
		}
	}
	public Map<String, String> values() {
		stop_editing();
		Map<String, String> m = new LinkedHashMap<String, String>();
		for (int i = 0; i < model.getRowCount(); i++) {
			m.put(String.valueOf(model.getValueAt(i, 0)), String.valueOf(model.getValueAt(i, 1)).trim());
		}
		return m;
	}
	private void stop_editing() {
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}
	}
}
