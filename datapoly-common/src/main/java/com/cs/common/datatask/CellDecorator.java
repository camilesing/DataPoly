// Use of this source code is governed by a BSD-style license
package com.cs.common.datatask;

/**
 * Optional extension point applied cell-by-cell after projection and just before
 * handing rows to the sink (masking, unit conversion, enrichment, ...).
 * Hosts register implementations as Spring beans; every decorator participates.
 */
public interface CellDecorator {

    Object decorate(String column, int columnIndex, Object value);
}
