import React from 'react';

const PetsCell = ({pet}) =>
    <div className="col-sm">
        <p>Name: {pet.name}</p>
        <p>Vendor: {pet.vendor}</p>
        <p>Type: {pet.type}</p>
    </div>

export default PetsCell;
