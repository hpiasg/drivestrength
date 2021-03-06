package de.uni_potsdam.hpi.asg.drivestrength.netlist.verilogparser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.uni_potsdam.hpi.asg.common.iohelper.FileHelper;
import de.uni_potsdam.hpi.asg.drivestrength.aggregatedcells.AggregatedCellLibrary;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.Netlist;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.elements.Module;

public class VerilogParser {
    protected static final Logger logger = LogManager.getLogger();

    private static final Pattern endmodulePattern = Pattern.compile("^\\s*endmodule\\s*$");
    private static final Pattern statementPattern = Pattern.compile("^.*;$");
    private static final Pattern escapeLiteralPattern = Pattern.compile("\\\\([^ ]*) ");

    private List<String> statements;
    private AggregatedCellLibrary aggregatedCellLibrary;
    private boolean replaceCellsBySingleStageGates;
    private String netlistName;

    public VerilogParser(File verilogFile, AggregatedCellLibrary aggregatedCellLibrary, boolean replaceCellsBySingleStageGates) {
        this.netlistName = basename(verilogFile.getName());
        this.statements = readVerilogStatementsFromFile(verilogFile);
        this.aggregatedCellLibrary = aggregatedCellLibrary;
        this.replaceCellsBySingleStageGates = replaceCellsBySingleStageGates;
        if (this.replaceCellsBySingleStageGates) {
            logger.warn("Replacing multi-stage cells with single-stage cells of equal pin count. Remove in production!");
        }
    }

    private String basename(String filename) {
        return filename.split("\\.(?=[^\\.]+$)")[0];
    }

    public VerilogParser(File verilogFile) {
        this.statements = readVerilogStatementsFromFile(verilogFile);
    }

    private List<String> readVerilogStatementsFromFile(File verilogFile) {
        List<String> lines = FileHelper.getInstance().readFile(verilogFile);
        assert(lines != null);
        List<String> statements = mergeMultilineStatements(lines);
        replaceEscapeLiterals(statements);
        return statements;
    }

    private List<String> mergeMultilineStatements(List<String> lines) {
        List<String> statements = new ArrayList<String>();
        boolean isMultiLineComment = false;

        String statement = "";
        String comment = "";
        for (String line: lines) {
            line = line.trim();
            if(line.contains("//")) {
                comment += line.substring(line.indexOf("//"));
                line = line.substring(0, line.indexOf("//"));
            }
            if(line.startsWith("/*")) {
                isMultiLineComment = true;
            }
            if(line.endsWith("*/")) {
                isMultiLineComment = false;
                continue;
            }
            if(line.startsWith("`timescale") || line.equals("") || isMultiLineComment) {
                continue;
            }
            statement += line;
            if (matches(line, statementPattern) || matches(line, endmodulePattern) ) {
                if(!comment.equals("")) {
                    statements.add(comment);
                }
                statements.add(statement);
                statement = "";
                comment = "";
            } else {
                statement += " ";
            }
        }

        return statements;
    }

    private void replaceEscapeLiterals(List<String> statements) {
        for (ListIterator<String> i = statements.listIterator(); i.hasNext();) {
            String statement = i.next();
            Matcher m = escapeLiteralPattern.matcher(statement);
            while (m.find()) {
                String escapeLiteral = m.group(1);
                statement = statement.replace("\\"+escapeLiteral+" ", cleanLiteral(escapeLiteral));
            }
            i.set(statement);
        }
    }

    private String cleanLiteral(String aLiteral) {
        return aLiteral.replaceAll("[^A-Za-z0-9 ]", "_");
    }

    private boolean matches(String aString, Pattern aPattern) {
        Matcher m = aPattern.matcher(aString);
        return m.matches();
    }

    public Netlist createNetlist() {
        logger.info("Parsing Verilog netlist...");

        Netlist netlist = new Netlist();

        netlist.setName(this.netlistName);

        List<String> currentModuleStatements = new ArrayList<String>();

        for (String statement: statements) {
            currentModuleStatements.add(statement);
            if (matches(statement, endmodulePattern)) {
                Module module = new VerilogModuleParser(currentModuleStatements, netlist,
                        aggregatedCellLibrary, replaceCellsBySingleStageGates).run();
                netlist.addModule(module);
                currentModuleStatements = new ArrayList<String>();
            }
        }

        logger.info("Netlist root module: " + netlist.getRootModule().getName());

        return netlist;
    }
}
