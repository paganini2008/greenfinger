/*
 * Copyright 2017-2026 Fred Feng (paganini.fy@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.greenfinger.record;

import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.github.greenfinger.core.model.Image;
import com.github.greenfinger.core.model.ResourceImage;

/**
 * Writes the shared image rows, each in a transaction of its own.
 *
 * <p>
 * Two pages fetched at the same moment can reference the same picture, and because the id is
 * derived from the bytes both threads compute the same one and both try to insert it. Checking
 * first does not help: the other transaction has not committed yet, so there is nothing to find.
 *
 * <p>
 * Three details make this work, and each of them was learned the hard way:
 *
 * <ul>
 * <li>The insert runs in its <b>own</b> transaction, so a violation does not roll back the page
 * that was in the middle of being saved.</li>
 * <li>The retry runs in <b>another</b> transaction rather than the failed one. PostgreSQL aborts a
 * transaction outright on a constraint violation and refuses every later statement in it, so
 * catching the exception and reading again inside the same transaction fails a second time --
 * which H2 tolerates and PostgreSQL does not.</li>
 * <li>Transactions are opened through a template rather than annotations, because a method calling
 * another method on the same bean bypasses the proxy that annotations rely on.</li>
 * </ul>
 *
 * <p>
 * SQLite is the exception, and joins the page's transaction instead. It locks the whole file to
 * write and gives each connection its own snapshot, so a second transaction writing while the page
 * transaction is still open makes the page fail with {@code SQLITE_BUSY_SNAPSHOT} -- on a single
 * crawl thread as readily as on sixteen, because the two transactions belong to the same thread.
 * Joining is safe there for the same reason separating was needed elsewhere: SQLite lets a
 * transaction carry on after a failed statement, so a duplicate image does not poison the page.
 * 
 * @Description: ImageWriter
 * @Author: Fred Feng
 * @Date: 30/08/2026
 * @Version 2.0.0
 */
public class ImageWriter {

    private final ImageRepository imageRepository;
    private final ResourceImageRepository resourceImageRepository;
    private final TransactionTemplate transactions;

    public ImageWriter(ImageRepository imageRepository,
            ResourceImageRepository resourceImageRepository,
            PlatformTransactionManager transactionManager) {
        this(imageRepository, resourceImageRepository, transactionManager, true);
    }

    /**
     * @param ownTransactions whether the image rows get transactions of their own. True everywhere
     *        but SQLite -- see the class comment.
     */
    public ImageWriter(ImageRepository imageRepository,
            ResourceImageRepository resourceImageRepository,
            PlatformTransactionManager transactionManager, boolean ownTransactions) {
        this.imageRepository = imageRepository;
        this.resourceImageRepository = resourceImageRepository;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(ownTransactions
                ? TransactionDefinition.PROPAGATION_REQUIRES_NEW
                : TransactionDefinition.PROPAGATION_REQUIRED);
    }

    public Image findOrCreate(String id, Supplier<Image> factory) {
        Optional<Image> existing = inNewTransaction(() -> imageRepository.findById(id));
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return inNewTransaction(() -> imageRepository.saveAndFlush(factory.get()));
        } catch (DataIntegrityViolationException e) {
            // somebody else inserted the identical bytes; read what they wrote, in a clean
            // transaction, because the failed one is no longer usable
            return inNewTransaction(() -> imageRepository.findById(id)).orElseThrow(() -> e);
        }
    }

    public ResourceImage findOrCreateReference(String id, Supplier<ResourceImage> factory) {
        Optional<ResourceImage> existing =
                inNewTransaction(() -> resourceImageRepository.findById(id));
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            return inNewTransaction(() -> resourceImageRepository.saveAndFlush(factory.get()));
        } catch (DataIntegrityViolationException e) {
            return inNewTransaction(() -> resourceImageRepository.findById(id))
                    .orElseThrow(() -> e);
        }
    }

    private <T> T inNewTransaction(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }

}
