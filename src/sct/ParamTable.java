package sct;

import java.awt.BorderLayout;
import java.awt.Component;
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
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

//Таблица параметров «имя — значение — описание» с поиском по имени и описанию. Значения редактируются
//текстом; проверка — при применении (Presets.set / PlantConfig.set сообщат об ошибке).
//Описания — из ParamDocs; ширина имени и значения — по содержимому, описание занимает остальное.
public class ParamTable extends JPanel {
	private static final long serialVersionUID = 1L;
	private final Map<String, String> docs;
	private final DefaultTableModel model = new DefaultTableModel(new Object[]{"parameter", "value", "description"}, 0) {
		private static final long serialVersionUID = 1L;
		public boolean isCellEditable(int row, int col) {
			return col == 1;
		}
	};
	private final JTable table = new JTable(model) {//true/false — галочкой; описание целиком — в подсказке
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
		public String getToolTipText(java.awt.event.MouseEvent e) {
			int row = rowAtPoint(e.getPoint());
			if (row < 0) {
				return null;
			}
			Object d = getValueAt(row, 2);
			return d == null || d.toString().isEmpty() ? null : d.toString();
		}
	};
	private final JTextField filter = new JTextField();

	public ParamTable() {
		this(Map.of());
	}
	public ParamTable(Map<String, String> docs) {
		super(new BorderLayout(4, 4));
		this.docs = docs;
		TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<DefaultTableModel>(model);
		table.setRowSorter(sorter);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
		filter.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { update(); }
			public void removeUpdate(DocumentEvent e) { update(); }
			public void changedUpdate(DocumentEvent e) { update(); }
			private void update() {
				String t = filter.getText().trim();
				sorter.setRowFilter(t.isEmpty() ? null : RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(t), 0, 2));
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
			model.addRow(new Object[]{r[0], v, docs.getOrDefault(r[0], "")});
		}
		fit_column(0);
		fit_column(1);
	}
	private void fit_column(int col) {//ширина по самому широкому значению и заголовку; дальше не меняется сама
		TableColumn c = table.getColumnModel().getColumn(col);
		Component h = table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table, c.getHeaderValue(), false, false, -1, col);
		int w = h.getPreferredSize().width;
		for (int row = 0; row < table.getRowCount(); row++) {
			Component cell = table.prepareRenderer(table.getCellRenderer(row, col), row, col);
			w = Math.max(w, cell.getPreferredSize().width);
		}
		w += 12;
		c.setMinWidth(w);
		c.setPreferredWidth(w);
		c.setMaxWidth(w);
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
