package cc.troikadumper;

import android.content.Context;
import android.widget.ArrayAdapter;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DumpListAdapter extends ArrayAdapter<DumpListAdapter.DumpListFilename> {
    protected static Pattern pattern;

    protected  Pattern getPattern() {
        if (pattern == null) {
            pattern = Pattern.compile(Dump.FILENAME_REGEXP);
        }
        return pattern;
    }

    public class DumpListFilename {

        protected String filename;

        public DumpListFilename(String filename) {
            this.filename = filename;
        }

        public String getFilename() {
            return filename;
        }

        public String toString() {
            Matcher m = getPattern().matcher(filename);
            if (m.matches()) {
                String info = "";
                Date d = new Date(
                        Integer.parseInt(m.group(1)) - 1900,
                        Integer.parseInt(m.group(2)) - 1,
                        Integer.parseInt(m.group(3)),
                        Integer.parseInt(m.group(4).substring(0, 2)),
                        Integer.parseInt(m.group(4).substring(2, 4)),
                        Integer.parseInt(m.group(4).substring(4, 6))
                );
                info += DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(d);
                info += "\nCard: " + Dump.formatCardNumber(Integer.parseInt(m.group(5))) + "  -  RUB: " + m.group(6);
                return info;
            } else {
                return "error parsing filename";
            }
        }
    }

    public DumpListAdapter(Context context, String[] filenames) {
        super(context, R.layout.dump_list_item, R.id.dump_list_item_label);

        Arrays.sort(filenames);
        List<String> filenamesList = Arrays.asList(filenames);
        Collections.reverse(filenamesList);

        for (String filename : filenamesList) {
            add(new DumpListFilename(filename));
        }
    }

}
