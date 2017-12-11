/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.mongodb.core;

import static java.util.Collections.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

import java.util.List;

import org.bson.Document;
import org.hamcrest.core.IsCollectionContaining;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.MongoTemplate.BatchAggregationLoader;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;

import com.mongodb.ReadPreference;

/**
 * @author Christoph Strobl
 */
@RunWith(MockitoJUnitRunner.class)
public class BatchAggregationLoaderUnitTests {

	static final TypedAggregation<Person> AGGREGATION = newAggregation(Person.class,
			project().and("firstName").as("name"));

	@Mock MongoTemplate template;
	@Mock Document aggregationResult;
	@Mock Document getMoreResult;

	BatchAggregationLoader loader;

	Document cursorWithoutMore = new Document("firstBatch", singletonList(new Document("name", "luke")));
	Document cursorWithMore = new Document("id", 123).append("firstBatch", singletonList(new Document("name", "luke")));
	Document cursorWithNoMore = new Document("id", 0).append("nextBatch", singletonList(new Document("name", "han")));

	@Before
	public void setUp() {
		loader = new BatchAggregationLoader(template, ReadPreference.primary(), 10);
	}

	@Test // DATAMONGO-1824
	public void shouldLoadJustOneBatchWhenAlreayDoneWithFirst() {

		when(template.executeCommand(any(Document.class), any(ReadPreference.class))).thenReturn(aggregationResult);
		when(aggregationResult.containsKey("cursor")).thenReturn(true);
		when(aggregationResult.get("cursor")).thenReturn(cursorWithoutMore);

		Document result = loader.aggregate("person", AGGREGATION, Aggregation.DEFAULT_CONTEXT);
		
		assertThat((List<Object>) result.get("result"),
				IsCollectionContaining.<Object> hasItem(new Document("name", "luke")));

		verify(template).executeCommand(any(Document.class), any(ReadPreference.class));
		verifyNoMoreInteractions(template);
	}

	@Test // DATAMONGO-1824
	public void shouldBatchLoadWhenRequired() {

		when(template.executeCommand(any(Document.class), any(ReadPreference.class))).thenReturn(aggregationResult)
				.thenReturn(getMoreResult);
		when(aggregationResult.containsKey("cursor")).thenReturn(true);
		when(aggregationResult.get("cursor")).thenReturn(cursorWithMore);
		when(getMoreResult.containsKey("cursor")).thenReturn(true);
		when(getMoreResult.get("cursor")).thenReturn(cursorWithNoMore);

		Document result = loader.aggregate("person", AGGREGATION, Aggregation.DEFAULT_CONTEXT);
		assertThat((List<Object>) result.get("result"),
				IsCollectionContaining.<Object> hasItems(new Document("name", "luke"), new Document("name", "han")));

		verify(template, times(2)).executeCommand(any(Document.class), any(ReadPreference.class));
		verifyNoMoreInteractions(template);
	}
}