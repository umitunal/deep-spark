/*
 * Copyright 2014, Stratio.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.deep.examples.java;

import com.google.common.collect.Lists;
import com.stratio.deep.config.ExtractorConfig;
import com.stratio.deep.core.context.DeepSparkContext;
import com.stratio.deep.extractor.server.ExtractorServer;
import com.stratio.deep.extractor.utils.ExtractorConstants;
import com.stratio.deep.rdd.CassandraCellExtractor;
import com.stratio.deep.testentity.TweetEntity;
import com.stratio.deep.testutils.ContextProperties;
import org.apache.log4j.Logger;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.rdd.RDD;
import scala.Tuple2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// !!Important!!

/**
 * Author: Emmanuelle Raffenne
 * Date..: 13-feb-2014
 */
public final class GroupingByColumn {

    private static final Logger LOG = Logger.getLogger(GroupingByColumn.class);

    private static List<Tuple2<String, Integer>> results;

    private GroupingByColumn() {
    }

    /**
     * Application entry point.
     *
     * @param args the arguments passed to the application.
     */
    public static void main(String[] args) {
        doMain(args);
    }

    /**
     * This is the method called by both main and tests.
     *
     * @param args
     */
    public static void doMain(String[] args) {
        String job = "java:groupingByColumn";

        String KEYSPACENAME = "test";
        String TABLENAME    = "tweets";
        String CQLPORT      = "9042";
        String RPCPORT      = "9160";
        String HOST         = "127.0.0.1";

        //Call async the Extractor netty Server
        ExecutorService es = Executors.newFixedThreadPool(3);
        final Future future = es.submit(new Callable() {
            public Object call() throws Exception {
                ExtractorServer.main(null);
                return null;
            }
        });


        // Creating the Deep Context
        ContextProperties p = new ContextProperties(args);
	    DeepSparkContext deepContext = new DeepSparkContext(p.getCluster(), job, p.getSparkHome(), p.getJars());

        // Create a configuration for the Extractor and initialize it
        ExtractorConfig<TweetEntity> config = new ExtractorConfig(TweetEntity.class);

        config.setExtractorImplClass(CassandraCellExtractor.class);
        config.setEntityClass(TweetEntity.class);
        Map<String, String> values = new HashMap<>();
        values.put(ExtractorConstants.KEYSPACE, KEYSPACENAME);
        values.put(ExtractorConstants.TABLE,    TABLENAME);
        values.put(ExtractorConstants.CQLPORT,  CQLPORT);
        values.put(ExtractorConstants.RPCPORT,  RPCPORT);
        values.put(ExtractorConstants.HOST,     HOST );

        config.setValues(values);

        // Creating the RDD
        RDD<TweetEntity> rdd = deepContext.createRDD(config);

        // grouping
        JavaPairRDD<String, Iterable<TweetEntity>> groups = rdd.toJavaRDD().groupBy(new Function<TweetEntity, String>() {
            @Override
            public String call(TweetEntity tableEntity) {
                return tableEntity.getAuthor();
            }
        });

        // counting elements in groups
        JavaPairRDD<String, Integer> counts = groups.mapToPair(new PairFunction<Tuple2<String,
                Iterable<TweetEntity>>, String,
                Integer>() {
            @Override
            public Tuple2<String, Integer> call(Tuple2<String, Iterable<TweetEntity>> t) {
                return new Tuple2<String, Integer>(t._1(), Lists.newArrayList(t._2()).size());
            }
        });

        // fetching the results
        results = counts.collect();

        LOG.info("Este es el resultado con groupBy: ");
        for (Tuple2 t : results) {
            LOG.info(t._1() + ": " + t._2().toString());
        }

        deepContext.stop();
    }

    public static List<Tuple2<String, Integer>> getResults() {
        return results;
    }
}
