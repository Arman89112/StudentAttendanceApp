package com.attendance.app.i18n;

import java.util.HashMap;
import java.util.Map;

/** Runtime switchable Chinese / English labels. */
public final class Lang {

    public enum Language { ZH, EN }

    private static Language current = Language.ZH;
    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        put("app.title", "点名助手", "Attendance");
        put("semester", "学期", "Semester");
        put("subject", "学科", "Subject");
        put("new.semester", "新建学期", "New semester");
        put("new.subject", "新建学科", "New subject");
        put("rename", "重命名", "Rename");
        put("delete", "删除", "Delete");
        put("name", "姓名", "Name");
        put("student.id", "学号", "Student ID");
        put("student.name", "学生姓名", "Student name");
        put("student.id.hint", "学号（纯数字）", "Student ID (digits)");
        put("search", "搜索姓名或学号", "Search name or ID");
        put("add", "添加", "Add");
        put("attendance.rate", "出勤率", "Rate");
        put("late.count", "迟到", "Late");
        put("present", "到", "Present");
        put("absent", "缺", "Absent");
        put("late", "迟", "Late");
        put("unmarked", "未标记", "Unmarked");
        put("settings", "科目设置", "Subject settings");
        put("week.count", "周数", "Weeks");
        put("lesson.count", "每周节数", "Lessons / week");
        put("save", "保存", "Save");
        put("cancel", "取消", "Cancel");
        put("import.roster", "导入名单", "Import students");
        put("import.attendance", "导入名单+考勤", "Import students + attendance");
        put("export", "导出 Excel", "Export Excel");
        put("mark.all.present", "本列全设为到", "Mark column present");
        put("clear.column", "清除本列", "Clear column");
        put("copy.roster", "复制其他学科名单", "Copy another class list");
        put("language", "语言", "Language");
        put("theme", "主题", "Theme");
        put("light", "日间", "Light");
        put("dark", "夜间", "Dark");
        put("students", "学生", "Students");
        put("marked", "已点", "Marked");
        put("no.subject", "请选择左侧的一门学科", "Pick a subject on the left");
        put("no.students", "还没有学生，请在上方输入姓名和学号添加", "No students yet, add a name and ID above");
        put("no.semester", "还没有学期，请点左下「新建学期」", "No semester yet, click New semester");
        put("duplicate.id", "该学号已存在", "That student ID already exists");
        put("added", "已添加", "Added");
        put("import.done", "导入完成", "Import finished");
        put("import.empty", "没有识别到数据", "Nothing recognised");
        put("export.done", "已导出", "Exported");
        put("confirm.delete", "确认删除？", "Delete?");
        put("confirm.delete.semester", "删除这个学期及全部学科和数据？", "Delete this semester, subjects and data?");
        put("confirm.delete.subject", "删除这门学科及其全部点名记录？", "Delete this subject and all records?");
        put("confirm.delete.student", "确认把「%s」移出本学科？", "Remove \"%s\" from this subject?");
        put("ok", "确定", "OK");
        put("hint.click", "左键：到 → 缺 → 迟 → 未标记    右键：清除    方向键移动  空格切换  Delete 清除",
                "Left click: present → absent → late → unmarked    Right click: clear    Arrows move  Space cycles  Delete clears");
        put("hint.focused.column", "批量操作作用于当前列", "Batch actions apply to the focused column");
        put("week.label", "第", "W");
        put("week.suffix", "周", "");
        put("lesson.label", "节", "L");
        put("roster.copied", "已复制", "Copied");
        put("nothing.to.copy", "没有可复制的学生", "Nothing to copy");
        put("export.pdf", "导出 PDF", "Export PDF");
        put("print", "打印", "Print");
        put("template", "下载模板", "Download template");
        put("stats", "统计图表", "Statistics");
        put("profiles", "学生总档案", "All students");
        put("stats.total", "全班总览", "Class overview");
        put("stats.weeks", "每周出勤", "Attendance by week");
        put("stats.share", "状态占比", "Status share");
        put("stats.students", "学生人数", "Students");
        put("stats.marked", "已点格子", "Marked cells");
        put("stats.average", "平均出勤率", "Average rate");
        put("stats.late", "迟到总数", "Late total");
        put("stats.perfect", "全勤人数", "Perfect attendance");
        put("profile.subjects", "学科数", "Subjects");
        put("profile.rate", "总出勤率", "Overall rate");
        put("pdf.saved", "PDF 已保存", "PDF saved");
        put("pdf.opened", "PDF 已生成并用默认程序打开", "PDF created and opened");
        put("import.merged", "新增 %d，已存在 %d", "Added %d, already existed %d");
        put("import.failed", "导入失败", "Import failed");
        put("template.saved", "模板已保存", "Template saved");
        put("empty.file", "文件里没有可识别的学生", "No students recognised in the file");
        put("close", "关闭", "Close");
        put("week.short", "第%d周", "W%d");
        put("actions", "操作", "Actions");
        put("edit.name", "改姓名", "Edit name");
        put("edit.id", "改学号", "Edit ID");
        put("delete.selected", "删除选中", "Delete selected");
        put("select.all", "全选", "Select all");
        put("clear.selection", "取消选择", "Clear selection");
        put("select.none", "请先勾选左侧方框选择学生", "Tick the checkboxes to select students first");
        put("confirm.delete.selected", "确认删除选中的 %d 名学生？", "Delete the %d selected students?");
        put("zoom.in", "放大", "Zoom in");
        put("zoom.out", "缩小", "Zoom out");
        put("zoom.reset", "重置缩放", "Reset zoom");
    }

    private static void put(String key, String zh, String en) {
        ZH.put(key, zh);
        EN.put(key, en);
    }

    public static Language get() {
        return current;
    }

    public static void set(Language language) {
        current = language;
    }

    public static void toggle() {
        current = current == Language.ZH ? Language.EN : Language.ZH;
    }

    public static String get(String key) {
        String value = current == Language.ZH ? ZH.get(key) : EN.get(key);
        if (value == null) {
            value = EN.getOrDefault(key, key);
        }
        return value;
    }
}
