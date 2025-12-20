package org.vetronauta.lev.bench;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

public class BenchRunner {

    public static void main(String[] args) throws Exception {
        // You can pass a regex as the first arg to filter benchmarks, otherwise defaults to SimpleBenchmark
        String include = args.length > 0 ? args[0] : ".*SimpleBenchmark.*";

        Options opt = new OptionsBuilder()
                .include(include)
                .forks(1)
                .threads(1)
                .shouldFailOnError(true)
                .build();

        new Runner(opt).run();
    }
}

