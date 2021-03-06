/**
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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
package org.jboss.pnc.datastore.predicates.rsql;

import com.google.common.base.Preconditions;
import com.mysema.query.types.expr.BooleanExpression;
import com.mysema.query.types.path.PathBuilder;
import cz.jirutka.rsql.parser.RSQLParser;
import cz.jirutka.rsql.parser.RSQLParserException;
import cz.jirutka.rsql.parser.ast.*;
import org.jboss.pnc.datastore.predicates.RSQLPredicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.Introspector;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RSQLNodeTravellerPredicate<Entity> implements RSQLPredicate {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private final Node rootNode;

    private final Class<Entity> selectingClass;

    private final QueryDSLTransformer<Entity> queryDSLTransformer = new QueryDSLTransformer<>();

    private final Map<Class<? extends ComparisonNode>, Transformer<Entity>> operations = new HashMap<>();

    public RSQLNodeTravellerPredicate(Class<Entity> entityClass, String rsql) throws RSQLParserException {
        operations.put(NotEqualNode.class, (pathBuilder, operand, arguments) -> queryDSLTransformer.createNodeTransformer(pathBuilder, operand, Arrays.asList(arguments.get(0)), "ne"));
        operations.put(InNode.class, (pathBuilder, operand, arguments) -> queryDSLTransformer.createNodeTransformer(pathBuilder, operand, arguments, "in"));
        operations.put(NotInNode.class, (pathBuilder, operand, arguments) -> queryDSLTransformer.createNodeTransformer(pathBuilder, operand, arguments, "notIn"));
        operations.put(EqualNode.class, (pathBuilder, operand, arguments) -> queryDSLTransformer.createNodeTransformer(pathBuilder, operand, Arrays.asList(arguments.get(0)), "eq"));

        rootNode = new RSQLParser().parse(rsql);
        selectingClass = entityClass;
    }

    @Override
    public BooleanExpression get() {

        // Using lower-cases string variables makes Entities with camelCase names unusable (i.e. BuildRecord)
        PathBuilder<Entity> pathBuilder = new PathBuilder<>(selectingClass, Introspector.decapitalize(selectingClass
                .getSimpleName()));

        RSQLNodeTraveller<BooleanExpression> visitor = new RSQLNodeTraveller<BooleanExpression>() {

            public BooleanExpression visit(LogicalNode node) {
                logger.debug("Parsing LogicalNode {}", node);
                return proceedEmbeddedNodes(node);
            }

            public BooleanExpression visit(ComparisonNode node) {
                logger.debug("Parsing ComparisonNode {}", node);
                return proceedSelection(node);
            }

            private BooleanExpression proceedSelection(ComparisonNode node) {
                Transformer<Entity> transformation = operations.get(node.getClass());
                Preconditions.checkArgument(transformation != null, "Operation not supported");
                BooleanExpression expression = transformation.transform(pathBuilder, node.getSelector(), node.getArguments());
                return expression;
            }

            private BooleanExpression proceedEmbeddedNodes(LogicalNode node) {
                Iterator<Node> iterator = node.iterator();
                if (node instanceof AndNode) {
                    return visit(iterator.next()).and(visit(iterator.next()));
                } else if (node instanceof OrNode) {
                    return visit(iterator.next()).or(visit(iterator.next()));
                } else {
                    throw new UnsupportedOperationException("Logical operation not supported");
                }
            }
        };

        return rootNode.accept(visitor);
    }



}
