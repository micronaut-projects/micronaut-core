import React from 'react';
import VendorsTableRow from './VendorsTableRow'

const VendorsTable = ({vendors}) => <table className='table'>
    <thead>
    <tr>
        <th scope='col'>Name</th>
        <th scope='col'>Pets</th>
    </tr>
    </thead>
    <tbody>
    {vendors.map(v => <VendorsTableRow vendor={v}/>)}
    </tbody>
</table>

export default VendorsTable;
