package de.uni_potsdam.hpi.asg.drivestrength.benchmarks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.uni_potsdam.hpi.asg.drivestrength.aggregatedcells.AggregatedCellLibrary;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.DelayEstimator;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.Netlist;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.annotating.InputDrivenAnnotator;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.annotating.LoadGraphAnnotator;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.annotating.PredecessorAnnotator;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.cleaning.NetlistAssignCleaner;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.cleaning.NetlistBundleSplitter;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.cleaning.NetlistFlattener;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.cleaning.NetlistInliner;
import de.uni_potsdam.hpi.asg.drivestrength.netlist.verilogparser.VerilogParser;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.AbstractDriveOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.AllLargestOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.EqualDelayMatrixOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.EqualStageEffortOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.NeighborStageEffortOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.NopOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.SelectForLoadOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.optimization.SimulatedAnnealingOptimizer;
import de.uni_potsdam.hpi.asg.drivestrength.remotesimulation.RemoteSimulation;
import de.uni_potsdam.hpi.asg.drivestrength.remotesimulation.RemoteSimulationResult;

public class BenchmarkRunner {
    private AggregatedCellLibrary cellLibrary;
    private String outFileName;
    private int count;
    private File remoteConfigFile;
    private long startTime;

    public BenchmarkRunner(AggregatedCellLibrary cellLibrary, File remoteConfigFile) {
        this.cellLibrary = cellLibrary;
        this.outFileName = "benchmarks-output/" + date() + ".csv";
        this.remoteConfigFile = remoteConfigFile;
    }

    private String date() {
        DateFormat dfmt = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        return dfmt.format(new Date());
    }

    public void run() {
        System.out.println("Running Benchmarks...");
        this.count = 0;
        this.startTime = System.currentTimeMillis();

        //String[] benchmarkNetlists = {"inc"};
        String[] benchmarkNetlists = {"inc", "mod10", "count10", "bufferx", "gcd", "mult"};
        double[] benchmarkOutCs = {0.0, 0.003, 0.012, 0.1, 1.0};
        boolean[] benchmarkLimitInputs = {true, false};

        int combinationCount = benchmarkNetlists.length * benchmarkOutCs.length * benchmarkLimitInputs.length;

        for (String netlistName : benchmarkNetlists) {
            File netlistFile = new File("netlists/benchmarks-original/" + netlistName + ".v");
            Netlist inlinedNetlist = this.loadNetlist(netlistFile);

            for (double outputC : benchmarkOutCs) {
                for (boolean limitInput : benchmarkLimitInputs) {
                    try {
                        runBenchmarkEntry(inlinedNetlist, outputC, limitInput, combinationCount);
                    } catch (IOException e) {
                        throw new Error(e);
                    }
                }
            }
        }
    }

    private Netlist loadNetlist(File netlistFile) {
        Netlist netlist = new VerilogParser(netlistFile, this.cellLibrary, false).createNetlist();

        new NetlistFlattener(netlist).run();

        Netlist inlinedNetlist = new NetlistInliner(netlist).run();

        new NetlistBundleSplitter(inlinedNetlist).run();
        new NetlistAssignCleaner(inlinedNetlist).run();


        return inlinedNetlist;
    }

    Map<String, AbstractDriveOptimizer> setupOptimizers(Netlist netlist, double outputC, boolean limitInput) {
        Map<String, AbstractDriveOptimizer> optimizers = new HashMap<>();

        Netlist netlistCopy;

        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("NOP", new NopOptimizer(netlistCopy));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("ESE-clamp", new EqualStageEffortOptimizer(netlistCopy, 100, true));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("ESE-free", new EqualStageEffortOptimizer(netlistCopy, 100, false));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("NSE-clamp", new NeighborStageEffortOptimizer(netlistCopy, 100, true));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("NSE-free", new NeighborStageEffortOptimizer(netlistCopy, 100, false));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("SFL", new SelectForLoadOptimizer(netlistCopy, 100));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("Top", new AllLargestOptimizer(netlistCopy));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("SA-step", new SimulatedAnnealingOptimizer(netlistCopy, false, 30));
        netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
        optimizers.put("SA-jump", new SimulatedAnnealingOptimizer(netlistCopy, true, 30));
        if (netlist.isAllSingleStage()) {
            netlistCopy = copyAndReAnnotateNetlist(netlist, outputC, limitInput);
            optimizers.put("EDM", new EqualDelayMatrixOptimizer(netlistCopy));
        }

        return optimizers;
    }


    private void runBenchmarkEntry(Netlist netlist, double outputC, boolean limitInput, int combinationCount) throws IOException {
        System.out.println("Benchmark Entry " + netlist.getName() + ", outputC: " + outputC + ", limitInput: " + limitInput);

        Map<String, AbstractDriveOptimizer> optimizers = setupOptimizers(netlist, outputC, limitInput);

        int totalcount = optimizers.size() * combinationCount;


        List<String> optimizerNamesSorted = new ArrayList<>(optimizers.keySet());
        Collections.sort(optimizerNamesSorted);
        for (String optimizerName : optimizerNamesSorted) {
            AbstractDriveOptimizer optimizer = optimizers.get(optimizerName);
            optimizer.run();

            int estimatedDelay = new DelayEstimator(optimizer.getNetlist(), false, false).run();

            RemoteSimulation rs = new RemoteSimulation(optimizer.getNetlist().getName(),
                                           optimizer.getNetlist().toVerilog(), this.remoteConfigFile,
                                           outputC, false, false);
            rs.run();
            RemoteSimulationResult rsResult = rs.getResult();

            String benchmarkOutput = "benchmark-entry,";
            benchmarkOutput += netlist.getName() + ",";
            benchmarkOutput += outputC + ",";
            benchmarkOutput += limitInput + ",";
            benchmarkOutput += optimizerName + ",";
            benchmarkOutput += estimatedDelay + ",";
            benchmarkOutput += rsResult.getSdfDelaySum("_orig") + ",";
            benchmarkOutput += rsResult.getSdfDelaySum("_noslew") + ",";
            benchmarkOutput += rsResult.getSdfDelaySum("_noslew_nowire") + ",";
            benchmarkOutput += rsResult.getTestbenchSuccessTime("_orig") + ",";
            benchmarkOutput += rsResult.getTestbenchSuccessTime("_noslew") + ",";
            benchmarkOutput += rsResult.getTestbenchSuccessTime("_noslew_nowire");

            PrintWriter fileOut = new PrintWriter(new BufferedWriter(new FileWriter(outFileName, true)));
            fileOut.println(benchmarkOutput);
            fileOut.close();

            count++;
            this.printProgress(totalcount);
        }


    }

    private void printProgress(int totalcount) {
        long currentTime = System.currentTimeMillis();
        long milliseconds = currentTime - startTime;
        int seconds = (int) (milliseconds / 1000) % 60 ;
        int minutes = (int) ((milliseconds / (1000*60)) % 60);
        int hours   = (int) ((milliseconds / (1000*60*60)) % 24);
        System.out.println("Benchmark progress: " + count + " of "
                        + totalcount + " (" + Math.round(100.0 * count / totalcount) + " %)"
                        + " after " + hours + "h " + minutes + "m " + seconds + "s");

    }

    private Netlist copyAndReAnnotateNetlist(Netlist originalNetlist, double outputC, boolean limitInput) {
        Netlist copiedNetlist = new Netlist(originalNetlist);
        new LoadGraphAnnotator(copiedNetlist, outputC).run();
        new PredecessorAnnotator(copiedNetlist).run();
        if (limitInput) {
            new InputDrivenAnnotator(copiedNetlist).run();
        }

        return copiedNetlist;
    }
}