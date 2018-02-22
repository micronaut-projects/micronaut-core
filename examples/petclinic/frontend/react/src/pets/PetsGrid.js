import React from 'react';
import PetsRow from './PetsRow'

const PetsGrid = ({pets}) => <div>
    {
        pets.reduce((ar, it, i) => {
            const index = Math.floor(i / 3);

            if (!ar[index]) {
                ar[index] = [];
            }

            ar[index].push(it);

            return ar;
        }, []).map(group => <PetsRow pets={group}/>)
    }

</div>

export default PetsGrid;
