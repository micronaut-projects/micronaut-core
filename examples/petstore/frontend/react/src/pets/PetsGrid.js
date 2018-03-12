import React from 'react';
import PetsRow from './PetsRow'
import {array} from 'prop-types'

const PetsGrid = ({pets}) => {

  const groupByThree = (array, pet, i) => {
    const index = Math.floor(i / 3);

    if (!array[index]) {
      array[index] = [];
    }

    array[index].push(pet);

    return array;
  }
  

  return <div>
    {pets.reduce(groupByThree, []).map((group, i) => <PetsRow key={i} pets={group} />)}
  </div>
}

PetsGrid.propTypes = {
  pets: array
}

export default PetsGrid;
