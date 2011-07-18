package org.stjs.javascript.jquery.plugins;

import static org.stjs.javascript.Global.$array;

import org.stjs.javascript.Array;
import org.stjs.javascript.Map;
import org.stjs.javascript.jquery.JQuery;

public class DatePickerOptions<FullJQuery extends JQuery<?>> {
	public boolean disabled = false;

	public Object altField = "";

	public String altFormat = "";

	public String appendText = "";

	public boolean autoSize = false;

	public String buttonImage = "";

	public boolean buttonImageOnly = false;

	public String buttonText = "...";

	public Object calculateWeek;// = $.datepicker.iso8601Week

	public boolean changeMonth = false;

	public boolean changeYear = false;

	public String closeText = "Done";

	public boolean constrainInput = true;

	public String currentText = "Today";

	public String dateFormat = "mm/dd/yy";

	public Array<String> dayNames = $array("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");

	public Array<String> dayNamesMin = $array("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa");

	public Array<String> dayNamesShort = $array("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat");

	public String defaultDate = null;

	public Object duration = "normal";

	public int firstDay = 0;

	public boolean gotoCurrent = false;

	public boolean hideIfNoPrevNext = false;

	public boolean isRTL = false;

	public Object maxDate = null;

	public Object minDate = null;

	public Array<String> monthNames = $array("January", "February", "March", "April", "May", "June", "July", "August",
			"September", "October", "November", "December");

	public Array<String> monthNamesShort = $array("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct",
			"Nov", "Dec");

	public boolean navigationAsDateFormat = false;

	public String nextText = "Next";

	public Object numberOfMonths = 1;

	public String prevText = "Prev";

	public boolean selectOtherMonths = false;

	public Object shortYearCutoff = "+10";

	public String showAnim = "show";

	public boolean showButtonPanel = false;

	public int showCurrentAtPos = 0;

	public boolean showMonthAfterYear = false;

	public String showOn = "focus";

	public Map<Object> showOptions;// = {}

	public boolean showOtherMonths = false;

	public boolean showWeek = false;

	public int stepMonths = 1;

	public String weekHeader = "Wk";

	public String yearRange = "c-10:c+10";

	public String yearSuffix = "";

	public UIEventHandler<DatePickerUI<FullJQuery>> create;

	public UIEventHandler<DatePickerUI<FullJQuery>> beforeShow;

	public UIEventHandler<DatePickerUI<FullJQuery>> beforeShowDay;
	public UIEventHandler<DatePickerUI<FullJQuery>> onChangeMonthYear;

	public UIEventHandler<DatePickerUI<FullJQuery>> onClose;

	public UIEventHandler<DatePickerUI<FullJQuery>> onSelect;
}
