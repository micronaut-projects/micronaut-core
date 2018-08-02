package io.micronaut.jdbc.metadata;

import javax.sql.DataSource;

/**
 * Provides access meta-data that is commonly available from most pooled
 * {@link DataSource} implementations.
 *
 * @author Stephane Nicoll
 * @since 2.0.0
 */
public interface DataSourcePoolMetadata {

	Integer getIdle();

	/**
	 * Return the usage of the pool as value between 0 and 1 (or -1 if the pool is not
	 * limited).
	 * <ul>
	 * <li>1 means that the maximum number of connections have been allocated</li>
	 * <li>0 means that no connection is currently active</li>
	 * <li>-1 means there is not limit to the number of connections that can be allocated
	 * </li>
	 * </ul>
	 * This may also return {@code null} if the data source does not provide the necessary
	 * information to compute the poll usage.
	 * @return the usage value or {@code null}
	 */
	Float getUsage();

	/**
	 * Return the current number of active connections that have been allocated from the
	 * data source or {@code null} if that information is not available.
	 * @return the number of active connections or {@code null}
	 */
	Integer getActive();

	/**
	 * Return the maximum number of active connections that can be allocated at the same
	 * time or {@code -1} if there is no limit. Can also return {@code null} if that
	 * information is not available.
	 * @return the maximum number of active connections or {@code null}
	 */
	Integer getMax();

	/**
	 * Return the minimum number of idle connections in the pool or {@code null} if that
	 * information is not available.
	 * @return the minimum number of active connections or {@code null}
	 */
	Integer getMin();

	/**
	 * Return the query to use to validate that a connection is valid or {@code null} if
	 * that information is not available.
	 * @return the validation query or {@code null}
	 */
	String getValidationQuery();

	/**
	 * The default auto-commit state of connections created by this pool. If not set
	 * ({@code null}), default is JDBC driver default (If set to null then the
	 * java.sql.Connection.setAutoCommit(boolean) method will not be called.)
	 * @return the default auto-commit state or {@code null}
	 */
	Boolean getDefaultAutoCommit();

}