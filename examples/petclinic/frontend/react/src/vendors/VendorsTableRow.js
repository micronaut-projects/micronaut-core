import React from 'react';

const VendorsTableRow = ({vendor}) =>
    <tr>
        <td>{vendor.name}</td>
        <td>{vendor.pets.length}</td>
    </tr>

export default VendorsTableRow;
